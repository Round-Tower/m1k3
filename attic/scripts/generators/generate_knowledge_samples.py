#!/usr/bin/env python3
"""
Generate Sample Documents for User Validation
Creates 5-10 high-quality sample documents per proposed category
"""

import json
from datetime import datetime
from pathlib import Path
import hashlib

print("🎨 Generating Knowledge Base Expansion Samples")
print("=" * 80)

# Generate unique IDs
def gen_id(title):
    return hashlib.md5(title.encode()).hexdigest()[:16]

samples = {
    "learning_curriculum": [
        {
            "id": f"curriculum_{gen_id('Kindergarten Math Basics')}",
            "title": "Kindergarten Math: Counting and Number Recognition",
            "category": "learning_curriculum",
            "intent": "explanation_request",
            "content": """Kindergarten math focuses on building number sense through counting, recognizing numerals, and understanding quantity relationships.

**Core Skills**: Count to 20 (or 100), recognize and write numerals 0-10, understand one-to-one correspondence (matching objects to numbers), compare quantities (more/less/same), basic shapes and patterns.

**Teaching Approaches**: Use manipulatives (blocks, counters), incorporate movement (count jumps, claps), connect to daily life (count snacks, toys), make it playful (number songs, games).

**Developmental Milestones**: By end of K, children should count 20+ objects accurately, write numbers 0-10, understand that numbers represent quantities, recognize basic patterns.

**Common Challenges**: Some children confuse numeral shapes (6 vs 9), struggle with one-to-one counting, or don't yet grasp that numbers are abstract concepts.

**Parent Support**: Count everything together, play board games with dice, ask "how many" questions, celebrate mistakes as learning opportunities. Math anxiety starts early - keep it positive!""",
            "metadata": {"complexity": "beginner", "age_group": "K-2", "keywords": ["kindergarten", "math", "counting", "number sense"]}
        },
        {
            "id": f"curriculum_{gen_id('High School Note Taking')}",
            "title": "High School Study Skills: Effective Note-Taking Methods",
            "category": "learning_curriculum",
            "intent": "instruction_request",
            "content": """Effective note-taking transforms passive listening into active learning, improving retention and understanding across all subjects.

**Popular Methods**: Cornell Notes (divided sections for notes, cues, summary), Mind Mapping (visual branching), Outline Method (hierarchical structure), Charting (columns for comparison).

**Best Practices**: Write in your own words (not verbatim), use abbreviations consistently, highlight key terms, review within 24 hours, add questions in margins for self-testing.

**Subject-Specific Tips**: Math/science benefit from worked examples and diagrams. History/literature need timelines and cause-effect relationships. Languages use vocabulary tables with pronunciation notes.

**Digital vs Paper**: Paper notes improve retention for many learners due to processing required. Digital notes allow searchability and multimedia. Hybrid approaches combine benefits.

**Research Backing**: Studies show handwritten note-takers perform better on conceptual questions than laptop users, likely because summarization requires deeper processing than typing verbatim.""",
            "metadata": {"complexity": "intermediate", "age_group": "9-12", "keywords": ["study skills", "note-taking", "high school", "learning strategies"]}
        }
    ],
    "world_folklore": [
        {
            "id": f"folklore_{gen_id('Japanese Yokai')}",
            "title": "Japanese Folklore: Yokai Supernatural Creatures",
            "category": "world_folklore",
            "intent": "factual_query",
            "content": """Yokai are supernatural entities in Japanese folklore, ranging from mischievous spirits to powerful demons, deeply embedded in cultural storytelling.

**What Are Yokai**: Unlike Western 'monsters,' yokai encompass a spectrum—some helpful, some harmful, most morally ambiguous. They embody natural phenomena, human emotions, or objects that gained sentience (tsukumogami).

**Famous Examples**: Kitsune (fox spirits with shape-shifting), Tengu (bird-like mountain spirits), Kappa (river creatures who challenge humans), Tanuki (trickster raccoon dogs), Yuki-onna (snow woman spirit).

**Cultural Role**: Yokai stories taught moral lessons, explained natural phenomena before science, and reflected societal anxieties. Parents used scary yokai tales to ensure children's safety (don't go near rivers alone—kappa!).

**Modern Influence**: Yokai appear extensively in anime, manga, and video games. Pokémon draws heavily from yokai. Studio Ghibli films feature traditional yokai. Global popularity of Japanese media introduced yokai worldwide.

**Scholarly Interest**: Yokai studies reveal how cultures process the unknown and maintain traditions through storytelling across generations.""",
            "metadata": {"complexity": "intermediate", "region": "East Asia", "keywords": ["yokai", "Japanese folklore", "supernatural", "mythology"]}
        },
        {
            "id": f"folklore_{gen_id('Anansi Spider')}",
            "title": "West African Folklore: Anansi the Spider Trickster",
            "category": "world_folklore",
            "intent": "factual_query",
            "content": """Anansi is a central figure in Akan folklore from Ghana, a spider who uses wit and cunning to overcome more powerful beings, teaching valuable life lessons.

**Character Traits**: Anansi embodies intelligence triumphing over strength, but also demonstrates flaws—greed, pride, and consequences of trickery. He's simultaneously hero and cautionary tale.

**Famous Stories**: Anansi buying all world stories from sky god Nyame, Anansi and the Moss-Covered Rock, How Anansi Got His Thin Waist. Each story blends entertainment with moral instruction.

**Cultural Transmission**: Anansi stories traveled with enslaved Africans to Caribbean and Americas, evolving into Br'er Anansi in Jamaica and influencing Br'er Rabbit tales. Oral traditions preserved African culture through colonization.

**Trickster Archetype**: Anansi shares characteristics with tricksters worldwide—Loki (Norse), Coyote (Native American), Reynard the Fox (European). This archetype appears across human cultures, suggesting universal themes.

**Modern Legacy**: Authors like Neil Gaiman ('Anansi Boys') introduced Anansi to new audiences, celebrating African diaspora storytelling traditions.""",
            "metadata": {"complexity": "intermediate", "region": "Africa", "keywords": ["Anansi", "trickster", "West African", "folklore", "oral tradition"]}
        }
    ],
    "pop_culture": [
        {
            "id": f"pop_{gen_id('Meme Culture')}",
            "title": "Meme Culture: Evolution of Internet Humor",
            "category": "pop_culture",
            "intent": "explanation_request",
            "content": """Memes are units of cultural information that spread rapidly online, evolving from simple image macros to complex multimedia formats shaping modern communication.

**Historical Evolution**: Early internet memes (2000s) like LOLcats and Rickrolling were primarily image-based. Twitter introduced text-based viral formats. TikTok brought short-form video memes with sound and editing.

**How Memes Spread**: Memes mutate as users remix and recontextualize them. Successful memes are relatable, easily modifiable, and timely. Platform algorithms amplify viral content exponentially.

**Cultural Significance**: Memes function as social commentary, in-jokes building community, and increasingly as political discourse. They compress complex ideas into digestible, shareable formats.

**Generational Differences**: Millennials favor image macros with text. Gen Z prefers absurdist, layered irony and video memes. Different platforms have distinct meme cultures (Reddit vs Instagram vs TikTok).

**Impact**: Memes influence marketing, activism, and political campaigns. Understanding meme culture is increasingly essential for cultural literacy and effective online communication.""",
            "metadata": {"complexity": "beginner", "era": "2000s-present", "keywords": ["memes", "internet culture", "viral content", "social media"]}
        },
        {
            "id": f"pop_{gen_id('Streaming Revolution')}",
            "title": "The Streaming Revolution: How Netflix Changed Entertainment",
            "category": "pop_culture",
            "intent": "explanation_request",
            "content": """Streaming services fundamentally transformed how people consume media, disrupting traditional television and film industries while changing viewer expectations.

**The Shift**: Netflix's transition from DVD rentals to streaming (2007) pioneered on-demand entertainment. Binge-watching entire seasons became normalized rather than waiting for weekly episodes.

**Industry Impact**: Traditional networks lost advertising revenue and viewership. Theaters faced competition from same-day streaming releases. Cable subscriptions declined ('cord-cutting'). Content creators gained new distribution platforms.

**The Streaming Wars**: Disney+, HBO Max, Amazon Prime, Apple TV+ entered the market, fragmenting content across subscriptions. Exclusive content became key differentiator. Some consumers experience 'subscription fatigue.'

**Content Changes**: Streaming platforms greenlight diverse, niche content that wouldn't work on traditional TV. Algorithm-driven recommendations shape viewing habits. International content (Korean dramas, anime) reached global audiences.

**Cultural Effects**: Shared cultural moments declined (fewer watch same show simultaneously). But global hits like 'Squid Game' or 'Stranger Things' still create worldwide conversations.""",
            "metadata": {"complexity": "intermediate", "era": "2007-present", "keywords": ["streaming", "Netflix", "entertainment", "binge-watching", "media"]}
        }
    ],
    "honest_politics": [
        {
            "id": f"politics_{gen_id('Democratic Elections')}",
            "title": "Understanding Democratic Elections: From Registration to Results",
            "category": "honest_politics",
            "intent": "explanation_request",
            "content": """Democratic elections allow citizens to choose representatives through voting processes that vary by country but share core principles of fairness and accountability.

**Basic Process**: Citizens register to vote (requirements vary), receive information about candidates and issues, cast ballots (in-person, by mail, or electronically), and votes are counted and certified.

**Electoral Systems**: First-past-the-post (plurality winner, used in US/UK), ranked-choice voting (voters rank preferences), proportional representation (parties receive seats matching vote percentage, common in Europe). Each system has trade-offs.

**Key Principles**: One person one vote, secret ballot to prevent coercion, accessible polling locations, transparent counting, mechanisms for recounts or challenges.

**Common Questions**: Why registration? (Prevents double-voting, verifies citizenship). Why different systems? (No perfect method; each optimizes different values like simplicity vs representativeness).

**Citizen Responsibilities**: Research candidates' positions, verify information from multiple sources, understand ballot measures, participate in primaries and local elections (often more impactful than national).

**Challenges**: Gerrymandering, voter suppression, misinformation. Healthy democracies continually work to address these issues.""",
            "metadata": {"complexity": "intermediate", "civic_education": True, "keywords": ["democracy", "elections", "voting", "civics"]}
        },
        {
            "id": f"politics_{gen_id('Media Literacy')}",
            "title": "Media Literacy: Evaluating Political News Sources",
            "category": "honest_politics",
            "intent": "instruction_request",
            "content": """Critical evaluation of political information sources is essential for informed citizenship, especially in an era of abundant information and misinformation.

**Source Evaluation**: Check author credentials, publication reputation, and funding sources. Established journalism follows editorial standards; opinion pieces and social media posts may not. Look for fact-checking labels and corrections policies.

**Bias Recognition**: All sources have perspectives, but credible outlets distinguish news from opinion. Check multiple sources across the political spectrum. Notice emotional language, unattributed claims, and cherry-picked data.

**Fact-Checking Process**: Verify claims with original sources (not just headlines). Use independent fact-checkers (PolitiFact, FactCheck.org, Snopes). Be skeptical of 'too good to be true' information confirming your beliefs.

**Red Flags**: Unnamed sources for major claims, extreme emotionally charged language, no corrections ever issued, circular sourcing (articles citing each other), clickbait headlines misrepresenting content.

**Healthy Practices**: Follow diverse sources, read full articles not just headlines, understand difference between reporting and analysis, recognize your own biases, pause before sharing inflammatory content.""",
            "metadata": {"complexity": "intermediate", "civic_education": True, "keywords": ["media literacy", "fact-checking", "news", "critical thinking"]}
        }
    ],
    "history_expanded": [
        {
            "id": f"history_{gen_id('Mali Empire')}",
            "title": "The Kingdom of Mali: Medieval African Superpower",
            "category": "historical_facts",
            "intent": "factual_query",
            "content": """The Mali Empire (1235-1600 CE) was one of history's wealthiest and most powerful civilizations, controlling vital trans-Saharan trade routes and fostering Islamic scholarship.

**Rise to Power**: Founded by Sundiata Keita after defeating the Sosso kingdom, Mali expanded across West Africa, controlling gold mines and salt trade routes. At its height, Mali was larger than Western Europe.

**Mansa Musa's Wealth**: Emperor Mansa Musa I (ruled 1312-1337) is considered history's richest person (adjusted for inflation). His legendary pilgrimage to Mecca (1324) distributed so much gold in Cairo it caused inflation for a decade.

**Cultural Achievements**: Timbuktu became a world-renowned center of Islamic learning with Sankore University attracting scholars globally. Extensive manuscript collections preserved knowledge in mathematics, astronomy, medicine, and law.

**Economic Foundation**: Mali controlled gold mines producing much of the world's gold supply. Salt from Saharan mines was equally valuable. Trans-Saharan trade networks connected sub-Saharan Africa to Mediterranean economies.

**Legacy**: Mali's history challenges misconceptions about medieval Africa, demonstrating sophisticated governance, economic systems, and intellectual culture.""",
            "metadata": {"complexity": "intermediate", "region": "Africa", "era": "Medieval", "keywords": ["Mali Empire", "Mansa Musa", "African history", "Timbuktu"]}
        }
    ],
    "geography_expanded": [
        {
            "id": f"geo_{gen_id('Amazon Rainforest')}",
            "title": "Amazon Rainforest: Biodiversity and Global Importance",
            "category": "geography_facts",
            "intent": "factual_query",
            "content": """The Amazon Rainforest, spanning 5.5 million square kilometers across nine South American countries, contains 10% of all species on Earth and critically regulates global climate.

**Geographic Scope**: Primarily in Brazil (60%), but also Peru, Colombia, Venezuela, Ecuador, Bolivia, Guyana, Suriname, and French Guiana. The Amazon River system drains the entire basin.

**Biodiversity**: Estimated 390 billion individual trees representing 16,000 species. Home to 2.5 million insect species, 40,000 plant species, 1,300 bird species, countless mammals, reptiles, amphibians. Many species remain undiscovered.

**Climate Regulation**: Functions as 'Earth's lungs,' producing 20% of global oxygen while absorbing massive CO2 amounts. Rainforest transpiration influences rainfall patterns across South America and globally.

**Human Communities**: Indigenous peoples have lived sustainably in the Amazon for 11,000+ years. Estimated 400-500 indigenous tribes, including uncontacted groups. Traditional knowledge invaluable for conservation.

**Conservation Threats**: Deforestation for cattle ranching, agriculture, logging, mining. Climate change creates feedback loops. About 17% has been deforested—scientists warn of approaching 'tipping point.'""",
            "metadata": {"complexity": "intermediate", "region": "South America", "keywords": ["Amazon", "rainforest", "biodiversity", "climate", "conservation"]}
        }
    ],
    "spiritualism_religion": [
        {
            "id": f"religion_{gen_id('Buddhism Four Truths')}",
            "title": "Buddhism Fundamentals: The Four Noble Truths",
            "category": "spiritualism_religion",
            "intent": "explanation_request",
            "content": """The Four Noble Truths form the foundation of Buddhist philosophy, outlining the nature of suffering and the path to liberation, taught by Siddhartha Gautama (the Buddha) in the 5th century BCE.

**The Four Truths**: (1) Dukkha—Life involves suffering and impermanence. (2) Samudaya—Suffering originates from attachment and desire. (3) Nirodha—Suffering can end by releasing attachments. (4) Magga—The Eightfold Path leads to suffering's cessation.

**The Eightfold Path**: Right understanding, intention, speech, action, livelihood, effort, mindfulness, and concentration. These practices develop wisdom, ethical conduct, and mental discipline.

**Key Concepts**: Impermanence (everything changes), no-self (anatta), karma (actions have consequences), nirvana (liberation from suffering cycle).

**Buddhist Branches**: Theravada (Southeast Asia, monastic emphasis), Mahayana (East Asia, compassion emphasis), Vajrayana (Tibet, tantric practices). All share Four Noble Truths core.

**Modern Relevance**: Buddhist mindfulness practices influence secular psychology, therapy, and stress reduction globally. Estimated 500+ million practitioners worldwide.""",
            "metadata": {"complexity": "intermediate", "religion": "Buddhism", "keywords": ["Buddhism", "Four Noble Truths", "meditation", "Eastern philosophy"]}
        },
        {
            "id": f"religion_{gen_id('Golden Rule')}",
            "title": "The Golden Rule Across World Religions",
            "category": "spiritualism_religion",
            "intent": "factual_query",
            "content": """The Golden Rule—treat others as you wish to be treated—appears across virtually all major religions and ethical systems, suggesting a universal human moral intuition.

**Religious Expressions**: Christianity—'Do unto others as you would have them do unto you' (Matthew 7:12). Islam—'None truly believes until he wishes for his brother what he wishes for himself' (Hadith). Judaism—'What is hateful to you, do not do to your fellow' (Talmud). Hinduism—'Do not do to others what would cause pain if done to you' (Mahabharata). Buddhism—'Hurt not others in ways you would find hurtful' (Udanavarga).

**Philosophical Formulations**: Confucius taught reciprocity as core virtue. Kant's Categorical Imperative mirrors the Golden Rule philosophically. Modern secular ethics incorporates similar principles.

**Why Universal?**: Reflects empathy and perspective-taking, fundamental human cognitive abilities. Reciprocity likely evolved as social cooperation mechanism. Appears independently across cultures.

**Practical Application**: Foundation for human rights, conflict resolution, and ethical decision-making across cultures. Provides common ground for interfaith dialogue.""",
            "metadata": {"complexity": "intermediate", "comparative": True, "keywords": ["Golden Rule", "ethics", "comparative religion", "morality"]}
        }
    ],
    "banter_humor": [
        {
            "id": f"humor_{gen_id('Puns Art')}",
            "title": "The Art of Puns: Why They're Simultaneously Loved and Groaned At",
            "category": "banter_humor",
            "intent": "explanation_request",
            "content": """Puns exploit multiple meanings or similar sounds of words to create humor, representing perhaps the oldest and most universal form of wordplay across languages and cultures.

**Types of Puns**: Homophonic (same sound, different meaning: 'I used to be a baker, but I couldn't make enough dough'), homographic (same spelling, different meaning: 'Time flies like an arrow; fruit flies like a banana').

**Why Puns Work**: Humor arises from unexpected cognitive shift when the brain recognizes dual meanings. The 'groan' reaction acknowledges cleverness while protesting linguistic trickery.

**Psychological Research**: Pun appreciation correlates with verbal intelligence and cognitive flexibility. Creating puns requires simultaneous processing of semantic and phonological information.

**Cultural Variations**: Puns are language-dependent and culture-specific. Translation nearly impossible. Each language has pun traditions—Japanese 'dajare,' Chinese '双关语.'

**Strategic Use**: Effective for memory aids (mnemonics), ice-breaking, showing linguistic wit. Overuse risks annoyance. Context and audience matter immensely.

**Historical Note**: Shakespeare used ~3,000 puns in his works. Puns were considered high wit in Elizabethan England, demonstrating linguistic mastery.""",
            "metadata": {"complexity": "intermediate", "humor_type": "wordplay", "keywords": ["puns", "humor", "wordplay", "linguistics"]}
        }
    ]
}

# Save samples
output_file = Path("knowledge/knowledge_expansion_samples.json")
output = {
    "generated_at": datetime.now().isoformat(),
    "purpose": "User validation samples before full 345-document expansion",
    "proposed_categories": list(samples.keys()),
    "total_samples": sum(len(docs) for docs in samples.values()),
    "samples": samples
}

with open(output_file, 'w', encoding='utf-8') as f:
    json.dump(output, f, indent=2, ensure_ascii=False)

# Print summary
print(f"\n✅ Generated validation samples")
print(f"   Total samples: {output['total_samples']}")
print(f"   Categories: {len(samples)}")
for cat, docs in samples.items():
    print(f"   • {cat}: {len(docs)} samples")

print(f"\n📁 Saved to: {output_file}")
print(f"   File size: {output_file.stat().st_size / 1024:.1f} KB")

print("\n" + "=" * 80)
print("✅ Sample generation complete!")
print("\nThese samples demonstrate:")
print("  • Document structure and quality standards")
print("  • Content depth and educational value")
print("  • Tone and style for each category")
print("  • Sensitive content handling (politics, religion)")
print("\nReview these samples before proceeding with full 345-document generation.")
