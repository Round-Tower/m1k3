#!/usr/bin/env python3
"""
M1K3 PWA Knowledge Base Builder
Creates structured knowledge chunks for browser-based RAG
"""

import json
import hashlib
import logging
from pathlib import Path
from typing import Dict, List, Tuple
from dataclasses import dataclass, asdict
import re

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

@dataclass
class KnowledgeChunk:
    """Structured knowledge chunk for RAG"""
    id: str
    text: str
    title: str
    category: str
    subcategory: str
    keywords: List[str]
    chunk_type: str  # "procedure", "facts", "qa", "reference"
    difficulty_level: str  # "beginner", "intermediate", "advanced"
    related_chunks: List[str]
    metadata: Dict

class KnowledgeBaseBuilder:
    """Builds structured knowledge base for M1K3 PWA RAG"""
    
    def __init__(self, output_dir: str = "../frontend/models"):
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
        self.chunks = []
        self.chunk_index = {}
        
    def create_chunk(self, text: str, title: str, category: str, 
                    subcategory: str = "", keywords: List[str] = None,
                    chunk_type: str = "facts", difficulty_level: str = "beginner",
                    related_chunks: List[str] = None, metadata: Dict = None) -> KnowledgeChunk:
        """Create a structured knowledge chunk"""
        
        # Generate unique ID
        chunk_id = hashlib.md5(f"{category}:{subcategory}:{title}".encode()).hexdigest()[:12]
        
        # Clean and format text
        text = self._clean_text(text)
        
        # Extract keywords if not provided
        if keywords is None:
            keywords = self._extract_keywords(text)
        
        chunk = KnowledgeChunk(
            id=chunk_id,
            text=text,
            title=title,
            category=category,
            subcategory=subcategory or category,
            keywords=keywords,
            chunk_type=chunk_type,
            difficulty_level=difficulty_level,
            related_chunks=related_chunks or [],
            metadata=metadata or {}
        )
        
        self.chunks.append(chunk)
        self.chunk_index[chunk_id] = chunk
        
        return chunk
    
    def _clean_text(self, text: str) -> str:
        """Clean and format text for optimal RAG performance"""
        # Remove extra whitespace
        text = re.sub(r'\s+', ' ', text.strip())
        
        # Ensure proper sentence endings
        if text and not text.endswith(('.', '!', '?', ':')):
            text += '.'
        
        return text
    
    def _extract_keywords(self, text: str) -> List[str]:
        """Extract relevant keywords from text"""
        # Simple keyword extraction - could be enhanced with NLP
        words = re.findall(r'\b[a-zA-Z]{3,}\b', text.lower())
        
        # Common stop words to remove
        stop_words = {
            'the', 'and', 'for', 'are', 'but', 'not', 'you', 'all', 'can', 'had', 
            'was', 'one', 'our', 'out', 'day', 'use', 'how', 'man', 'new', 'now',
            'way', 'may', 'say', 'each', 'which', 'their', 'time', 'will', 'about',
            'would', 'there', 'could', 'other', 'after', 'first', 'well', 'also'
        }
        
        keywords = [w for w in words if w not in stop_words and len(w) > 3]
        
        # Return unique keywords, max 10
        return list(dict.fromkeys(keywords))[:10]
    
    def build_eco_diagnostics_knowledge(self):
        """Build comprehensive eco-diagnostics knowledge base"""
        logger.info("🌱 Building eco-diagnostics knowledge base...")
        
        # Solar Energy Assessment
        self.create_chunk(
            text="Solar panel efficiency depends on location, orientation, shading, and local weather patterns. South-facing panels with 30-45 degree tilt maximize energy production in most northern hemisphere locations. Calculate annual sunlight hours and solar irradiance to estimate energy output.",
            title="Solar Panel Placement and Efficiency Factors",
            category="renewable_energy",
            subcategory="solar_assessment",
            keywords=["solar", "panels", "efficiency", "orientation", "sunlight", "energy"],
            chunk_type="procedure",
            difficulty_level="beginner"
        )
        
        self.create_chunk(
            text="To calculate solar potential: 1) Measure roof area in square feet 2) Multiply by 0.75 to account for unusable space 3) Divide by panel size (typically 18 sq ft) 4) Multiply by panel wattage (300-400W typical) 5) Multiply by peak sun hours per day 6) Multiply by 0.85 efficiency factor.",
            title="Solar Energy Calculation Formula",
            category="renewable_energy", 
            subcategory="solar_assessment",
            keywords=["calculation", "formula", "roof", "wattage", "efficiency"],
            chunk_type="procedure",
            difficulty_level="intermediate"
        )
        
        # Energy Efficiency
        self.create_chunk(
            text="Home energy audit identifies inefficiencies and conservation opportunities. Key areas include insulation, air leaks, heating/cooling systems, water heating, lighting, and appliances. Use thermal imaging or professional assessment to detect heat loss patterns.",
            title="Home Energy Audit Basics",
            category="energy_efficiency",
            subcategory="home_audit",
            keywords=["audit", "insulation", "heating", "cooling", "efficiency"],
            chunk_type="procedure",
            difficulty_level="beginner"
        )
        
        self.create_chunk(
            text="LED lighting uses 75% less energy than incandescent bulbs and lasts 25 times longer. Replace high-usage bulbs first for maximum savings. Calculate payback: (LED cost - incandescent cost) / (annual electricity savings). Typical payback is 6-12 months.",
            title="LED Lighting Cost-Benefit Analysis",
            category="energy_efficiency",
            subcategory="lighting",
            keywords=["LED", "lighting", "savings", "payback", "efficiency"],
            chunk_type="qa",
            difficulty_level="beginner"
        )
        
        # Water Conservation
        self.create_chunk(
            text="Water conservation reduces utility bills and environmental impact. Install low-flow fixtures, fix leaks promptly, collect rainwater, use native plants, and optimize irrigation timing. A single dripping faucet wastes 3,000+ gallons annually.",
            title="Water Conservation Strategies",
            category="water_conservation",
            subcategory="home_conservation",
            keywords=["water", "conservation", "fixtures", "leaks", "irrigation"],
            chunk_type="facts",
            difficulty_level="beginner"
        )
        
        self.create_chunk(
            text="Rainwater harvesting calculation: Roof area (sq ft) × rainfall (inches) × 0.623 = gallons collected per inch of rain. Install first-flush diverters and proper filtration for potable use. Check local regulations for collection limits and permits.",
            title="Rainwater Harvesting System Design",
            category="water_conservation",
            subcategory="rainwater_harvesting",
            keywords=["rainwater", "harvesting", "calculation", "filtration", "permits"],
            chunk_type="procedure",
            difficulty_level="intermediate"
        )
        
        # Waste Reduction
        self.create_chunk(
            text="The waste hierarchy: Reduce, Reuse, Recycle, Rot (compost). Reducing consumption has the highest environmental impact. Conduct a waste audit to identify reduction opportunities. Track waste generation weekly to measure improvement.",
            title="Waste Reduction Hierarchy and Assessment",
            category="waste_reduction",
            subcategory="waste_hierarchy",
            keywords=["waste", "reduce", "reuse", "recycle", "compost", "audit"],
            chunk_type="facts",
            difficulty_level="beginner"
        )
        
        self.create_chunk(
            text="Composting reduces organic waste by 30-40% while creating nutrient-rich soil amendment. Maintain carbon-nitrogen ratio of 30:1 using browns (leaves, paper) and greens (food scraps, grass). Turn weekly and maintain moisture like a wrung-out sponge.",
            title="Home Composting Process and Ratios",
            category="waste_reduction",
            subcategory="composting",
            keywords=["compost", "organic", "carbon", "nitrogen", "moisture"],
            chunk_type="procedure",
            difficulty_level="intermediate"
        )
        
        # Sustainable Transportation
        self.create_chunk(
            text="Transportation accounts for 29% of greenhouse gas emissions. Alternatives include walking, cycling, public transit, carpooling, and electric vehicles. Calculate your transportation carbon footprint using annual mileage and vehicle efficiency.",
            title="Sustainable Transportation Options",
            category="transportation",
            subcategory="alternatives",
            keywords=["transportation", "emissions", "electric", "public", "carbon"],
            chunk_type="facts",
            difficulty_level="beginner"
        )
        
        self.create_chunk(
            text="Electric vehicle (EV) cost comparison: Calculate total cost of ownership including purchase price, electricity vs gas costs, maintenance savings, and incentives. EVs typically save $1,000-2,000 annually in operating costs after accounting for electricity rates.",
            title="Electric Vehicle Economic Analysis",
            category="transportation",
            subcategory="electric_vehicles",
            keywords=["electric", "vehicle", "cost", "savings", "incentives"],
            chunk_type="procedure",
            difficulty_level="intermediate"
        )
        
        # Indoor Air Quality
        self.create_chunk(
            text="Indoor air quality affects health and energy efficiency. Common pollutants include VOCs, particulates, humidity, and CO2. Improve through ventilation, air purification, source control, and indoor plants. Monitor with air quality sensors.",
            title="Indoor Air Quality Management",
            category="air_quality",
            subcategory="indoor_air",
            keywords=["air", "quality", "VOCs", "ventilation", "purification"],
            chunk_type="facts",
            difficulty_level="beginner"
        )
        
        # Sustainable Materials
        self.create_chunk(
            text="Choose sustainable materials based on lifecycle assessment, durability, recyclability, and environmental impact. Prioritize local sourcing, renewable resources, and low-toxicity options. Consider embodied energy and end-of-life disposal.",
            title="Sustainable Material Selection Criteria",
            category="materials",
            subcategory="selection_criteria",
            keywords=["materials", "sustainable", "lifecycle", "renewable", "local"],
            chunk_type="reference",
            difficulty_level="intermediate"
        )
        
        logger.info(f"✅ Created {len(self.chunks)} eco-diagnostics knowledge chunks")
    
    def build_health_knowledge(self):
        """Build health and fitness knowledge base"""
        logger.info("🏃 Building health and fitness knowledge base...")
        
        # Exercise Basics
        self.create_chunk(
            text="Regular exercise improves cardiovascular health, strength, flexibility, and mental wellbeing. The CDC recommends 150 minutes of moderate aerobic activity or 75 minutes of vigorous activity weekly, plus 2+ days of strength training.",
            title="Exercise Guidelines and Benefits",
            category="fitness",
            subcategory="exercise_basics",
            keywords=["exercise", "cardiovascular", "strength", "aerobic", "guidelines"],
            chunk_type="facts",
            difficulty_level="beginner"
        )
        
        # Nutrition Basics
        self.create_chunk(
            text="Balanced nutrition includes macronutrients (carbohydrates, proteins, fats) and micronutrients (vitamins, minerals). Focus on whole foods, adequate hydration, and portion control. Calculate daily caloric needs based on age, sex, weight, height, and activity level.",
            title="Nutrition Fundamentals and Caloric Needs",
            category="nutrition",
            subcategory="basics",
            keywords=["nutrition", "macronutrients", "calories", "hydration", "portions"],
            chunk_type="reference",
            difficulty_level="beginner"
        )
        
        # Sleep Optimization
        self.create_chunk(
            text="Quality sleep requires 7-9 hours nightly with consistent timing. Create sleep hygiene through dark, cool environment (65-68°F), limited screen time before bed, regular schedule, and avoiding caffeine 6+ hours before sleep.",
            title="Sleep Hygiene and Optimization",
            category="wellness",
            subcategory="sleep",
            keywords=["sleep", "hygiene", "schedule", "temperature", "caffeine"],
            chunk_type="procedure",
            difficulty_level="beginner"
        )
        
        logger.info(f"✅ Added {len(self.chunks) - len([c for c in self.chunks if c.category in ['renewable_energy', 'energy_efficiency', 'water_conservation', 'waste_reduction', 'transportation', 'air_quality', 'materials']])} health knowledge chunks")
    
    def export_knowledge_base(self, filename: str = "knowledge_base.json") -> str:
        """Export knowledge base to JSON format"""
        output_path = self.output_dir / filename
        
        # Convert chunks to serializable format
        serializable_chunks = [asdict(chunk) for chunk in self.chunks]
        
        # Create knowledge base structure
        knowledge_base = {
            "version": "1.0.0",
            "created_timestamp": int(time.time()),
            "total_chunks": len(self.chunks),
            "categories": list(set(chunk.category for chunk in self.chunks)),
            "chunk_types": list(set(chunk.chunk_type for chunk in self.chunks)),
            "difficulty_levels": list(set(chunk.difficulty_level for chunk in self.chunks)),
            "chunks": serializable_chunks,
            "statistics": self._generate_statistics()
        }
        
        # Save to file
        with open(output_path, 'w') as f:
            json.dump(knowledge_base, f, indent=2, ensure_ascii=False)
        
        logger.info(f"📚 Knowledge base exported to: {output_path}")
        logger.info(f"📊 Total size: {output_path.stat().st_size / 1024:.1f} KB")
        
        return str(output_path)
    
    def _generate_statistics(self) -> Dict:
        """Generate statistics about the knowledge base"""
        stats = {
            "total_chunks": len(self.chunks),
            "categories": {},
            "chunk_types": {},
            "difficulty_levels": {},
            "avg_text_length": 0,
            "total_keywords": 0
        }
        
        total_length = 0
        total_keywords = 0
        
        for chunk in self.chunks:
            # Category stats
            stats["categories"][chunk.category] = stats["categories"].get(chunk.category, 0) + 1
            
            # Chunk type stats
            stats["chunk_types"][chunk.chunk_type] = stats["chunk_types"].get(chunk.chunk_type, 0) + 1
            
            # Difficulty stats
            stats["difficulty_levels"][chunk.difficulty_level] = stats["difficulty_levels"].get(chunk.difficulty_level, 0) + 1
            
            # Text length
            total_length += len(chunk.text)
            total_keywords += len(chunk.keywords)
        
        stats["avg_text_length"] = total_length // len(self.chunks) if self.chunks else 0
        stats["total_keywords"] = total_keywords
        
        return stats
    
    def create_embeddings_ready_export(self, filename: str = "embeddings_ready.jsonl") -> str:
        """Export chunks in format ready for embeddings generation"""
        output_path = self.output_dir / filename
        
        with open(output_path, 'w') as f:
            for chunk in self.chunks:
                # Create combined text for embedding
                combined_text = f"{chunk.title}. {chunk.text}"
                
                # Create JSONL entry
                entry = {
                    "id": chunk.id,
                    "text": combined_text,
                    "title": chunk.title,
                    "category": chunk.category,
                    "subcategory": chunk.subcategory,
                    "keywords": chunk.keywords,
                    "chunk_type": chunk.chunk_type,
                    "difficulty_level": chunk.difficulty_level
                }
                
                f.write(json.dumps(entry, ensure_ascii=False) + '\n')
        
        logger.info(f"📄 Embeddings-ready export created: {output_path}")
        return str(output_path)

def main():
    """Main function for command line usage"""
    import argparse
    import time
    
    parser = argparse.ArgumentParser(description="Build knowledge base for M1K3 PWA RAG")
    parser.add_argument("--output", default="../frontend/models",
                       help="Output directory for knowledge base")
    parser.add_argument("--domains", nargs="+", choices=["eco", "health", "all"], default=["all"],
                       help="Knowledge domains to build")
    
    args = parser.parse_args()
    
    builder = KnowledgeBaseBuilder(args.output)
    
    # Build requested domains
    if "eco" in args.domains or "all" in args.domains:
        builder.build_eco_diagnostics_knowledge()
    
    if "health" in args.domains or "all" in args.domains:
        builder.build_health_knowledge()
    
    # Export knowledge base
    kb_path = builder.export_knowledge_base()
    embeddings_path = builder.create_embeddings_ready_export()
    
    print("\n" + "="*60)
    print("📚 KNOWLEDGE BASE BUILD SUMMARY")
    print("="*60)
    print(f"Total chunks: {len(builder.chunks)}")
    print(f"Categories: {len(set(chunk.category for chunk in builder.chunks))}")
    print(f"Knowledge base: {kb_path}")
    print(f"Embeddings ready: {embeddings_path}")
    print("✅ Knowledge base build complete!")
    
    return 0

if __name__ == "__main__":
    import time
    exit(main())