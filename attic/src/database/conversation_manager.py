#!/usr/bin/env python3
"""
Conversation Manager - DuckDB Integration
Handles conversation persistence, search, and retrieval for M1K3
"""

import os
import uuid
import json
import duckdb
from datetime import datetime, timedelta
from typing import Dict, List, Optional, Any, Tuple
from pathlib import Path
from dataclasses import dataclass, asdict

@dataclass
class ConversationRecord:
    """Represents a single conversation exchange with vector embeddings"""
    id: str
    session_id: str
    timestamp: datetime
    user_input: str
    ai_response: str
    response_time_ms: int
    tokens_used: int
    voice_enabled: bool = False
    avatar_active: bool = False
    rag_used: bool = False
    personality_type: str = "default"
    metadata: Dict[str, Any] = None
    user_embedding: Optional[List[float]] = None
    response_embedding: Optional[List[float]] = None
    conversation_summary: Optional[str] = None

    def __post_init__(self):
        if self.metadata is None:
            self.metadata = {}

@dataclass
class SessionRecord:
    """Represents a conversation session"""
    session_id: str
    start_time: datetime
    end_time: Optional[datetime] = None
    total_queries: int = 0
    total_tokens: int = 0
    personality_used: str = "default"
    features_used: List[str] = None

    def __post_init__(self):
        if self.features_used is None:
            self.features_used = []

class ConversationManager:
    """Manages conversation persistence using DuckDB"""

    def __init__(self, db_path: str = "databases/m1k3_conversations.duckdb"):
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(exist_ok=True, parents=True)

        # Initialize database connection
        self.conn = duckdb.connect(str(self.db_path))
        self._initialize_schema()

    def _initialize_schema(self):
        """Create database tables if they don't exist"""

        # Install and load VSS extension for vector similarity search
        try:
            self.conn.execute("INSTALL vss")
            self.conn.execute("LOAD vss")
        except Exception:
            pass  # Extension may already be installed/loaded

        # Conversations table with vector embeddings
        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS conversations (
                id VARCHAR PRIMARY KEY,
                session_id VARCHAR NOT NULL,
                timestamp TIMESTAMP NOT NULL,
                user_input TEXT NOT NULL,
                ai_response TEXT NOT NULL,
                response_time_ms INTEGER NOT NULL,
                tokens_used INTEGER DEFAULT 0,
                voice_enabled BOOLEAN DEFAULT FALSE,
                avatar_active BOOLEAN DEFAULT FALSE,
                rag_used BOOLEAN DEFAULT FALSE,
                personality_type VARCHAR DEFAULT 'default',
                metadata JSON,
                user_embedding FLOAT[384],
                response_embedding FLOAT[384],
                conversation_summary TEXT
            )
        """)

        # Migrate existing table to add vector columns if they don't exist
        self._migrate_conversations_table()

        # Try to create HNSW indexes for fast vector similarity search
        try:
            self.conn.execute("""
                CREATE INDEX IF NOT EXISTS idx_user_embedding
                ON conversations USING HNSW (user_embedding)
            """)
            self.conn.execute("""
                CREATE INDEX IF NOT EXISTS idx_response_embedding
                ON conversations USING HNSW (response_embedding)
            """)
        except Exception:
            # HNSW indexes may fail in persistent databases without experimental flag
            pass

        # Sessions table
        self.conn.execute("""
            CREATE TABLE IF NOT EXISTS sessions (
                session_id VARCHAR PRIMARY KEY,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                total_queries INTEGER DEFAULT 0,
                total_tokens INTEGER DEFAULT 0,
                personality_used VARCHAR DEFAULT 'default',
                features_used JSON
            )
        """)

        # Create indexes for performance
        try:
            self.conn.execute("CREATE INDEX IF NOT EXISTS idx_conversations_session ON conversations(session_id)")
            self.conn.execute("CREATE INDEX IF NOT EXISTS idx_conversations_timestamp ON conversations(timestamp)")
            self.conn.execute("CREATE INDEX IF NOT EXISTS idx_conversations_search ON conversations(user_input, ai_response)")
            self.conn.execute("CREATE INDEX IF NOT EXISTS idx_sessions_start_time ON sessions(start_time)")
        except:
            # Indexes might already exist
            pass

        self.conn.commit()

    def _migrate_conversations_table(self):
        """Migrate existing conversations table to add vector columns"""
        try:
            # Check if vector columns already exist
            result = self.conn.execute("PRAGMA table_info(conversations)").fetchall()
            existing_columns = {row[1] for row in result}  # row[1] is column name

            # Add missing vector columns
            if 'user_embedding' not in existing_columns:
                self.conn.execute("ALTER TABLE conversations ADD COLUMN user_embedding FLOAT[384]")
                print("✅ Added user_embedding column to conversations table")

            if 'response_embedding' not in existing_columns:
                self.conn.execute("ALTER TABLE conversations ADD COLUMN response_embedding FLOAT[384]")
                print("✅ Added response_embedding column to conversations table")

            if 'conversation_summary' not in existing_columns:
                self.conn.execute("ALTER TABLE conversations ADD COLUMN conversation_summary TEXT")
                print("✅ Added conversation_summary column to conversations table")

            self.conn.commit()

        except Exception as e:
            print(f"⚠️ Migration warning (table may not exist yet): {e}")

    def start_session(self, session_id: str, personality_type: str = "default") -> SessionRecord:
        """Start a new conversation session"""
        session = SessionRecord(
            session_id=session_id,
            start_time=datetime.now(),
            personality_used=personality_type
        )

        self.conn.execute("""
            INSERT INTO sessions (session_id, start_time, personality_used, features_used)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (session_id) DO UPDATE SET
                start_time = EXCLUDED.start_time,
                personality_used = EXCLUDED.personality_used
        """, [session.session_id, session.start_time, session.personality_used,
              json.dumps(session.features_used)])

        self.conn.commit()
        return session

    def end_session(self, session_id: str, total_queries: int = 0, total_tokens: int = 0):
        """End a conversation session"""
        self.conn.execute("""
            UPDATE sessions
            SET end_time = ?, total_queries = ?, total_tokens = ?
            WHERE session_id = ?
        """, [datetime.now(), total_queries, total_tokens, session_id])

        self.conn.commit()

    def add_conversation(self, conversation: ConversationRecord) -> str:
        """Add a conversation to the database with automatic embedding generation"""
        if not conversation.id:
            conversation.id = str(uuid.uuid4())

        # Generate embeddings and summary if not provided
        if not conversation.user_embedding or not conversation.response_embedding:
            try:
                from .conversation_embedder import get_conversation_embedder
                embedder = get_conversation_embedder()

                if embedder.is_available():
                    user_emb, response_emb = embedder.embed_conversation(
                        conversation.user_input, conversation.ai_response
                    )
                    conversation.user_embedding = conversation.user_embedding or user_emb
                    conversation.response_embedding = conversation.response_embedding or response_emb

                    if not conversation.conversation_summary:
                        conversation.conversation_summary = embedder.generate_conversation_summary(
                            conversation.user_input, conversation.ai_response
                        )

            except Exception as e:
                print(f"⚠️ Embedding generation failed: {e}")

        self.conn.execute("""
            INSERT INTO conversations
            (id, session_id, timestamp, user_input, ai_response, response_time_ms,
             tokens_used, voice_enabled, avatar_active, rag_used, personality_type, metadata,
             user_embedding, response_embedding, conversation_summary)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, [
            conversation.id, conversation.session_id, conversation.timestamp,
            conversation.user_input, conversation.ai_response, conversation.response_time_ms,
            conversation.tokens_used, conversation.voice_enabled, conversation.avatar_active,
            conversation.rag_used, conversation.personality_type, json.dumps(conversation.metadata),
            conversation.user_embedding, conversation.response_embedding, conversation.conversation_summary
        ])

        # Update session statistics
        self.conn.execute("""
            UPDATE sessions
            SET total_queries = total_queries + 1,
                total_tokens = total_tokens + ?
            WHERE session_id = ?
        """, [conversation.tokens_used, conversation.session_id])

        self.conn.commit()
        return conversation.id

    def search_conversations(self, query: str, limit: int = 50) -> List[Dict]:
        """Search conversations by content"""
        search_query = f"%{query.lower()}%"

        result = self.conn.execute("""
            SELECT c.*, s.personality_used, s.start_time as session_start
            FROM conversations c
            LEFT JOIN sessions s ON c.session_id = s.session_id
            WHERE LOWER(c.user_input) LIKE ? OR LOWER(c.ai_response) LIKE ?
            ORDER BY c.timestamp DESC
            LIMIT ?
        """, [search_query, search_query, limit])

        return [dict(zip([col[0] for col in result.description], row)) for row in result.fetchall()]

    def search_conversations_by_similarity(self, query: str, limit: int = 10, threshold: float = 0.7) -> List[Dict]:
        """Search conversations using vector similarity"""
        try:
            from .conversation_embedder import get_conversation_embedder
            embedder = get_conversation_embedder()

            if not embedder.is_available():
                print("⚠️ Embeddings not available, falling back to text search")
                return self.search_conversations(query, limit)

            # Generate embedding for query
            query_embedding = embedder.embed_text(query)
            if not query_embedding:
                return self.search_conversations(query, limit)

            # Get all conversations with embeddings
            result = self.conn.execute("""
                SELECT id, session_id, timestamp, user_input, ai_response,
                       response_time_ms, tokens_used, personality_type,
                       user_embedding, response_embedding, conversation_summary
                FROM conversations
                WHERE user_embedding IS NOT NULL
                ORDER BY timestamp DESC
                LIMIT 1000
            """).fetchall()

            columns = [col[0] for col in self.conn.description]
            conversations = [dict(zip(columns, row)) for row in result]

            # Calculate similarities
            similarities = []
            for conv in conversations:
                # Check similarity with user input
                user_sim = 0.0
                if conv['user_embedding']:
                    user_sim = embedder.cosine_similarity(query_embedding, conv['user_embedding'])

                # Check similarity with AI response
                response_sim = 0.0
                if conv['response_embedding']:
                    response_sim = embedder.cosine_similarity(query_embedding, conv['response_embedding'])

                # Use maximum similarity
                max_similarity = max(user_sim, response_sim)

                if max_similarity >= threshold:
                    conv['similarity_score'] = max_similarity
                    conv['user_similarity'] = user_sim
                    conv['response_similarity'] = response_sim
                    similarities.append(conv)

            # Sort by similarity and return top results
            similarities.sort(key=lambda x: x['similarity_score'], reverse=True)
            return similarities[:limit]

        except Exception as e:
            print(f"❌ Vector similarity search failed: {e}")
            return self.search_conversations(query, limit)

    def find_related_conversations(self, conversation_id: str, limit: int = 5, threshold: float = 0.6) -> List[Dict]:
        """Find conversations related to a specific conversation"""
        try:
            from .conversation_embedder import get_conversation_embedder
            embedder = get_conversation_embedder()

            if not embedder.is_available():
                return []

            # Get the reference conversation
            result = self.conn.execute("""
                SELECT user_embedding, response_embedding
                FROM conversations
                WHERE id = ?
            """, [conversation_id]).fetchone()

            if not result or not (result[0] or result[1]):
                return []

            # Use the embedding that's available (prefer user input)
            ref_embedding = result[0] if result[0] else result[1]

            # Search for similar conversations
            similar = self.search_conversations_by_similarity("", limit * 2, threshold)

            # Filter out the reference conversation itself
            related = [conv for conv in similar if conv['id'] != conversation_id]

            return related[:limit]

        except Exception as e:
            print(f"❌ Related conversation search failed: {e}")
            return []

    def get_conversation_clusters(self, min_similarity: float = 0.8, min_cluster_size: int = 2) -> List[List[Dict]]:
        """Group conversations into similarity clusters"""
        try:
            from .conversation_embedder import get_conversation_embedder
            embedder = get_conversation_embedder()

            if not embedder.is_available():
                return []

            # Get all conversations with embeddings
            result = self.conn.execute("""
                SELECT id, user_input, ai_response, user_embedding, response_embedding,
                       timestamp, personality_type, conversation_summary
                FROM conversations
                WHERE user_embedding IS NOT NULL
                ORDER BY timestamp DESC
                LIMIT 500
            """).fetchall()

            columns = [col[0] for col in self.conn.description]
            conversations = [dict(zip(columns, row)) for row in result]

            if len(conversations) < min_cluster_size:
                return []

            # Simple clustering algorithm
            clusters = []
            used_conversations = set()

            for i, conv1 in enumerate(conversations):
                if conv1['id'] in used_conversations:
                    continue

                cluster = [conv1]
                used_conversations.add(conv1['id'])

                # Find similar conversations
                for j, conv2 in enumerate(conversations[i+1:], i+1):
                    if conv2['id'] in used_conversations:
                        continue

                    # Calculate similarity between user inputs
                    if conv1['user_embedding'] and conv2['user_embedding']:
                        similarity = embedder.cosine_similarity(
                            conv1['user_embedding'], conv2['user_embedding']
                        )

                        if similarity >= min_similarity:
                            cluster.append(conv2)
                            used_conversations.add(conv2['id'])

                # Only keep clusters with minimum size
                if len(cluster) >= min_cluster_size:
                    clusters.append(cluster)

            return clusters

        except Exception as e:
            print(f"❌ Conversation clustering failed: {e}")
            return []

    def get_recent_conversations(self, limit: int = 20) -> List[Dict]:
        """Get recent conversations"""
        result = self.conn.execute("""
            SELECT c.*, s.personality_used, s.start_time as session_start
            FROM conversations c
            LEFT JOIN sessions s ON c.session_id = s.session_id
            ORDER BY c.timestamp DESC
            LIMIT ?
        """, [limit])

        return [dict(zip([col[0] for col in result.description], row)) for row in result.fetchall()]

    def get_session_conversations(self, session_id: str) -> List[Dict]:
        """Get all conversations from a specific session"""
        result = self.conn.execute("""
            SELECT * FROM conversations
            WHERE session_id = ?
            ORDER BY timestamp ASC
        """, [session_id])

        return [dict(zip([col[0] for col in result.description], row)) for row in result.fetchall()]

    def get_conversation_stats(self, days: int = 30) -> Dict:
        """Get conversation statistics for the last N days"""
        start_date = datetime.now() - timedelta(days=days)

        stats = self.conn.execute("""
            SELECT
                COUNT(*) as total_conversations,
                COUNT(DISTINCT session_id) as total_sessions,
                SUM(tokens_used) as total_tokens,
                AVG(response_time_ms) as avg_response_time,
                COUNT(CASE WHEN voice_enabled THEN 1 END) as voice_conversations,
                COUNT(CASE WHEN avatar_active THEN 1 END) as avatar_conversations,
                COUNT(CASE WHEN rag_used THEN 1 END) as rag_conversations,
                MIN(timestamp) as earliest_conversation,
                MAX(timestamp) as latest_conversation
            FROM conversations
            WHERE timestamp >= ?
        """, [start_date]).fetchone()

        columns = [col[0] for col in self.conn.description]
        return dict(zip(columns, stats))

    def export_conversations(self, format_type: str = "json", output_path: Optional[str] = None,
                           session_id: Optional[str] = None, days: Optional[int] = None) -> str:
        """Export conversations in various formats"""

        # Build query based on filters
        where_clauses = []
        params = []

        if session_id:
            where_clauses.append("session_id = ?")
            params.append(session_id)

        if days:
            start_date = datetime.now() - timedelta(days=days)
            where_clauses.append("timestamp >= ?")
            params.append(start_date)

        where_clause = " WHERE " + " AND ".join(where_clauses) if where_clauses else ""

        if not output_path:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            output_path = f"conversations_export_{timestamp}.{format_type}"

        if format_type.lower() == "csv":
            self.conn.execute(f"""
                COPY (SELECT * FROM conversations{where_clause} ORDER BY timestamp)
                TO '{output_path}' (FORMAT CSV, HEADER)
            """, params)
        elif format_type.lower() == "json":
            result = self.conn.execute(f"""
                SELECT * FROM conversations{where_clause} ORDER BY timestamp
            """, params)

            conversations = [dict(zip([col[0] for col in result.description], row))
                           for row in result.fetchall()]

            # Convert datetime objects to strings for JSON serialization
            for conv in conversations:
                if isinstance(conv.get('timestamp'), datetime):
                    conv['timestamp'] = conv['timestamp'].isoformat()

            with open(output_path, 'w') as f:
                json.dump(conversations, f, indent=2, default=str)
        elif format_type.lower() == "parquet":
            self.conn.execute(f"""
                COPY (SELECT * FROM conversations{where_clause} ORDER BY timestamp)
                TO '{output_path}' (FORMAT PARQUET)
            """, params)
        else:
            raise ValueError(f"Unsupported export format: {format_type}")

        return output_path

    def cleanup_old_conversations(self, days: int = 365) -> int:
        """Clean up conversations older than specified days"""
        cutoff_date = datetime.now() - timedelta(days=days)

        # Count conversations to be deleted
        count_result = self.conn.execute("""
            SELECT COUNT(*) FROM conversations WHERE timestamp < ?
        """, [cutoff_date]).fetchone()
        count_to_delete = count_result[0] if count_result else 0

        if count_to_delete > 0:
            # Delete old conversations
            self.conn.execute("DELETE FROM conversations WHERE timestamp < ?", [cutoff_date])

            # Clean up orphaned sessions
            self.conn.execute("""
                DELETE FROM sessions
                WHERE session_id NOT IN (
                    SELECT DISTINCT session_id FROM conversations
                )
            """)

            self.conn.commit()

        return count_to_delete

    def close(self):
        """Close database connection"""
        if self.conn:
            self.conn.close()

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()

# Global instance for easy access
_conversation_manager = None

def get_conversation_manager() -> ConversationManager:
    """Get or create global conversation manager instance"""
    global _conversation_manager
    if _conversation_manager is None:
        _conversation_manager = ConversationManager()
    return _conversation_manager