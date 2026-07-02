#!/usr/bin/env python3
"""
M1K3 Expanded Synthetic Document Generator
Enhanced with general knowledge, pop culture, and entertainment content
"""

import json
import random
import time
from typing import Dict, List, Any, Optional, Generator
from dataclasses import dataclass
from pathlib import Path
import hashlib

# Import M1K3 components
from src.rag.m1k3_rag_engine import RAGDocument, KnowledgeBase, M1K3RAGEngine
from src.utils.intent_classification_system import UserIntent
from synthetic_document_generator import DocumentTemplate, SyntheticDocumentGenerator

class ExpandedSyntheticGenerator(SyntheticDocumentGenerator):
    """Enhanced synthetic generator with general knowledge and pop culture"""
    
    def __init__(self, knowledge_base_path: str = "knowledge/expanded_knowledge_base.json"):
        super().__init__(knowledge_base_path)
        # Override with expanded templates
        self.templates = self._initialize_expanded_templates()
        
    def _initialize_expanded_templates(self) -> Dict[str, List[DocumentTemplate]]:
        """Initialize comprehensive template library including pop culture and general knowledge"""
        
        # Start with original technical templates
        original_templates = super()._initialize_templates()
        
        # Add expanded categories
        expanded_templates = {
            **original_templates,  # Keep all original technical content
            
            # GENERAL KNOWLEDGE CATEGORIES
            'historical_facts': [
                DocumentTemplate(
                    category="historical_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Historical Fact: {event} in {year}",
                    content_template="In {year}, {event} {description}. This event {significance} and {impact}. {interesting_detail}",
                    tags=["history", "facts", "{period}", "world-history"],
                    variables={
                        "year": [1776, 1969, 1989, 1066, 1492, 1914, 1945, 1865, 1963, 1991, 2001, 1929],
                        "event": [
                            "the Declaration of Independence was signed",
                            "humans first landed on the Moon",
                            "the Berlin Wall fell",
                            "the Battle of Hastings took place",
                            "Christopher Columbus reached the Americas",
                            "World War I began",
                            "World War II ended",
                            "the American Civil War ended",
                            "President Kennedy was assassinated", 
                            "the Soviet Union dissolved",
                            "the September 11 attacks occurred",
                            "the Stock Market crashed"
                        ],
                        "period": ["ancient", "medieval", "modern", "contemporary", "20th-century"],
                        "description": [
                            "marking a pivotal moment in history",
                            "changing the course of civilization",
                            "shaping the modern world",
                            "revolutionizing society",
                            "transforming human understanding"
                        ],
                        "significance": [
                            "marked the beginning of a new era",
                            "ended an important chapter in history",
                            "brought about significant social change",
                            "shifted the balance of world power",
                            "inspired movements worldwide"
                        ],
                        "impact": [
                            "continues to influence us today",
                            "laid the foundation for modern society",
                            "changed international relations forever",
                            "sparked technological advancement",
                            "reshaped cultural understanding"
                        ],
                        "interesting_detail": [
                            "Many people don't know that this event almost didn't happen due to weather conditions.",
                            "Interestingly, this occurred on the same day as another major historical event.",
                            "The full impact of this event wasn't realized until years later.",
                            "This event was witnessed by millions of people around the world.",
                            "The planning for this event took several years to complete."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="historical_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Ancient Civilization: {civilization}",
                    content_template="The {civilization} civilization {achievement} around {time_period}. They were known for {famous_for} and their influence on {influence_area}. {legacy_fact}",
                    tags=["history", "ancient", "civilization", "{civilization}"],
                    variables={
                        "civilization": ["Egyptian", "Roman", "Greek", "Mesopotamian", "Chinese", "Mayan", "Inca", "Aztec"],
                        "time_period": ["3000 BCE", "2000 BCE", "1000 BCE", "500 BCE", "100 CE", "400 CE"],
                        "achievement": [
                            "built monumental structures",
                            "developed advanced mathematics",
                            "created sophisticated writing systems",
                            "established complex trade networks",
                            "invented revolutionary technologies"
                        ],
                        "famous_for": [
                            "their architectural marvels",
                            "advanced astronomical knowledge", 
                            "sophisticated art and culture",
                            "military innovations",
                            "philosophical contributions"
                        ],
                        "influence_area": [
                            "modern engineering",
                            "contemporary mathematics",
                            "current legal systems",
                            "today's architectural design",
                            "modern political thought"
                        ],
                        "legacy_fact": [
                            "Many of their innovations are still used today.",
                            "Their cultural influence spread across continents.",
                            "Archaeological discoveries continue to reveal their sophistication.",
                            "Their knowledge was preserved and built upon by later civilizations.",
                            "Modern society owes many foundational concepts to their innovations."
                        ]
                    }
                )
            ],
            
            'science_facts': [
                DocumentTemplate(
                    category="science_facts",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Science Fact: {phenomenon} Explained",
                    content_template="{phenomenon} occurs because {explanation}. This is {classification} and can be observed {observation}. {fun_fact}",
                    tags=["science", "{field}", "facts", "nature"],
                    variables={
                        "phenomenon": [
                            "Lightning", "Rainbows", "Ocean tides", "Photosynthesis", "Magnetism",
                            "Gravity", "Sound waves", "Light refraction", "Evaporation", "Earthquakes",
                            "DNA replication", "Hibernation", "Migration patterns", "Bioluminescence"
                        ],
                        "field": ["physics", "chemistry", "biology", "geology", "astronomy"],
                        "explanation": [
                            "electrical charges build up in clouds and discharge",
                            "sunlight is refracted through water droplets",
                            "the Moon's gravitational pull affects Earth's oceans",
                            "plants convert sunlight into chemical energy",
                            "moving electric charges create magnetic fields"
                        ],
                        "classification": [
                            "a natural physical process",
                            "a fascinating biological mechanism",
                            "an important chemical reaction",
                            "a fundamental force of nature",
                            "a complex environmental interaction"
                        ],
                        "observation": [
                            "during storms and in laboratory experiments",
                            "after rain when the sun shines",
                            "twice daily at coastal areas",
                            "in all green plants during daylight",
                            "using special instruments and compass needles"
                        ],
                        "fun_fact": [
                            "Scientists are still discovering new aspects of this phenomenon.",
                            "This process happens millions of times every day around the world.",
                            "Ancient civilizations created myths to explain this occurrence.",
                            "Modern technology has allowed us to study this in incredible detail.",
                            "This phenomenon plays a crucial role in Earth's ecosystem."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="science_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Amazing Animal Fact: {animal}",
                    content_template="{animal} are remarkable because they can {ability}. These {animal_type} {habitat_info} and {diet_info}. {conservation_note}",
                    tags=["science", "animals", "biology", "nature"],
                    variables={
                        "animal": [
                            "Dolphins", "Octopuses", "Elephants", "Bees", "Wolves", "Penguins",
                            "Chameleons", "Hummingbirds", "Spiders", "Whales", "Ants", "Eagles"
                        ],
                        "animal_type": ["mammals", "birds", "insects", "reptiles", "marine animals"],
                        "ability": [
                            "communicate using complex languages",
                            "solve puzzles and use tools",
                            "remember locations for decades",
                            "navigate using Earth's magnetic field",
                            "change colors instantly for camouflage"
                        ],
                        "habitat_info": [
                            "live in diverse ecosystems around the world",
                            "have adapted to extreme environments",
                            "create complex social structures",
                            "migrate thousands of miles annually",
                            "build intricate homes and shelters"
                        ],
                        "diet_info": [
                            "have specialized feeding strategies",
                            "play crucial roles in their food chains",
                            "help pollinate plants and disperse seeds",
                            "maintain ecological balance in their habitats",
                            "have evolved unique hunting or foraging techniques"
                        ],
                        "conservation_note": [
                            "Conservation efforts are helping protect their populations.",
                            "Climate change is affecting their natural habitats.",
                            "Scientists continue to study their behaviors and needs.",
                            "Many species face challenges from human activities.",
                            "Educational programs help people appreciate their importance."
                        ]
                    }
                )
            ],
            
            'geography_facts': [
                DocumentTemplate(
                    category="geography_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Geography: {location} Facts",
                    content_template="{location} is {description} located in {region}. It's known for {famous_for} and has a {climate_type} climate. {interesting_fact}",
                    tags=["geography", "world", "{continent}", "places"],
                    variables={
                        "location": [
                            "Mount Everest", "Amazon Rainforest", "Sahara Desert", "Great Barrier Reef",
                            "Antarctica", "Mariana Trench", "Yellowstone", "Niagara Falls",
                            "Grand Canyon", "Great Wall of China", "Stonehenge", "Machu Picchu"
                        ],
                        "continent": ["asia", "africa", "europe", "americas", "oceania", "antarctica"],
                        "region": [
                            "the Himalayas between Nepal and Tibet",
                            "South America spanning multiple countries",
                            "North Africa covering several nations",
                            "northeastern Australia in the Coral Sea",
                            "the southernmost continent"
                        ],
                        "description": [
                            "the world's highest mountain peak",
                            "the largest tropical rainforest",
                            "the world's largest hot desert",
                            "the largest coral reef system",
                            "the coldest and driest continent"
                        ],
                        "famous_for": [
                            "its extreme altitude and challenging climbs",
                            "incredible biodiversity and oxygen production",
                            "vast sand dunes and extreme temperatures", 
                            "colorful marine life and coral formations",
                            "unique wildlife and pristine wilderness"
                        ],
                        "climate_type": [
                            "extremely cold and harsh",
                            "hot and humid tropical",
                            "hot and dry desert",
                            "warm tropical marine",
                            "polar with extreme weather"
                        ],
                        "interesting_fact": [
                            "The location experiences unique weather patterns found nowhere else on Earth.",
                            "Millions of people visit or depend on this location each year.",
                            "Scientists study this location to understand Earth's climate systems.",
                            "This location plays a crucial role in global environmental balance.",
                            "The formation of this location took millions of years of geological processes."
                        ]
                    }
                )
            ],
            
            # POP CULTURE CATEGORIES
            'movies_tv': [
                DocumentTemplate(
                    category="movies_tv",
                    intent=UserIntent.CASUAL_CONVERSATION,
                    title_template="{genre} Entertainment: {title_type}",
                    content_template="{genre} {media_type} often feature {common_elements} and appeal to audiences who enjoy {appeal_factors}. Popular examples include stories about {story_themes}. {cultural_impact}",
                    tags=["entertainment", "movies", "tv", "{genre}"],
                    variables={
                        "genre": [
                            "Science Fiction", "Fantasy", "Comedy", "Drama", "Action", "Horror",
                            "Romance", "Documentary", "Animation", "Thriller", "Mystery", "Adventure"
                        ],
                        "media_type": ["movies", "TV shows", "series", "films"],
                        "title_type": ["Classics", "Modern Hits", "Hidden Gems", "Fan Favorites", "Award Winners"],
                        "common_elements": [
                            "compelling characters and engaging storylines",
                            "impressive special effects and cinematography",
                            "memorable dialogue and iconic scenes",
                            "talented actors and creative directors",
                            "innovative storytelling techniques"
                        ],
                        "appeal_factors": [
                            "escapism and imagination",
                            "emotional connection and relatability",
                            "excitement and adrenaline",
                            "intellectual stimulation and mystery",
                            "humor and lighthearted entertainment"
                        ],
                        "story_themes": [
                            "heroic journeys and personal growth",
                            "love, friendship, and human relationships",
                            "good versus evil conflicts",
                            "technological advancement and its implications",
                            "historical events and cultural experiences"
                        ],
                        "cultural_impact": [
                            "These stories often influence fashion, language, and social trends.",
                            "Many become part of shared cultural experiences across generations.",
                            "They can shape public opinion and spark important conversations.",
                            "The best examples become timeless classics referenced for decades.",
                            "They provide common ground for people to connect and discuss."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="movies_tv",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Entertainment Industry: {aspect}",
                    content_template="The entertainment industry's {aspect} involves {process} and requires {skills}. {industry_fact} This field {career_info} and {future_trends}",
                    tags=["entertainment", "industry", "behind-scenes", "careers"],
                    variables={
                        "aspect": [
                            "movie production", "TV show creation", "special effects", "sound design",
                            "cinematography", "screenwriting", "directing", "acting", "editing"
                        ],
                        "process": [
                            "collaboration between many creative professionals",
                            "months or years of planning and execution",
                            "combining artistic vision with technical expertise",
                            "balancing creative goals with budget constraints",
                            "coordinating complex logistics and schedules"
                        ],
                        "skills": [
                            "creativity, technical knowledge, and communication",
                            "patience, attention to detail, and problem-solving",
                            "artistic vision and collaborative teamwork",
                            "adaptability and stress management",
                            "business acumen and creative innovation"
                        ],
                        "industry_fact": [
                            "The industry employs millions of people in various creative and technical roles.",
                            "Technology continues to revolutionize how content is created and distributed.",
                            "Streaming platforms have changed how audiences consume entertainment.",
                            "Independent creators now have more opportunities than ever before.",
                            "Global audiences influence content creation and distribution strategies."
                        ],
                        "career_info": [
                            "offers diverse opportunities for people with different talents",
                            "can be highly competitive but also very rewarding",
                            "benefits from continuous learning and skill development",
                            "includes both traditional and emerging digital platforms",
                            "values both technical expertise and creative innovation"
                        ],
                        "future_trends": [
                            "virtual reality and interactive media are growing rapidly.",
                            "artificial intelligence is beginning to assist in content creation.",
                            "social media platforms are becoming major content distributors.",
                            "audiences demand more diverse and inclusive storytelling.",
                            "environmental sustainability is becoming increasingly important."
                        ]
                    }
                )
            ],
            
            'music_culture': [
                DocumentTemplate(
                    category="music_culture",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Music Style: {genre} Explained",
                    content_template="{genre} music originated {origin} and is characterized by {characteristics}. Artists in this genre often use {instruments} and the style influences {cultural_influence}. {fun_musical_fact}",
                    tags=["music", "{genre}", "culture", "arts"],
                    variables={
                        "genre": [
                            "Jazz", "Rock", "Hip-Hop", "Classical", "Electronic", "Folk", "Blues",
                            "Country", "Reggae", "Pop", "R&B", "Punk", "Metal", "Indie"
                        ],
                        "origin": [
                            "in the early 20th century in the American South",
                            "in the 1950s from rhythm and blues influences", 
                            "in 1970s New York from DJ culture",
                            "in European courts during the Baroque period",
                            "with synthesizers and computers in the 1970s"
                        ],
                        "characteristics": [
                            "complex rhythms and improvisation",
                            "strong beat and electric guitar sounds",
                            "rhythmic speaking over beats and samples",
                            "structured compositions and orchestral arrangements",
                            "synthesized sounds and digital production"
                        ],
                        "instruments": [
                            "guitars, drums, bass, and keyboards",
                            "turntables, samplers, and drum machines",
                            "violins, cellos, pianos, and wind instruments",
                            "synthesizers, computers, and electronic controllers",
                            "traditional acoustic instruments and vocals"
                        ],
                        "cultural_influence": [
                            "fashion trends and lifestyle choices",
                            "social movements and political expression",
                            "language development and slang",
                            "dance styles and performance art",
                            "technology adoption and innovation"
                        ],
                        "fun_musical_fact": [
                            "This genre has evolved continuously, creating many sub-genres and fusion styles.",
                            "Many famous artists started in this genre before exploring other musical territories.",
                            "The genre's influence extends far beyond music into art, fashion, and social culture.",
                            "Digital technology has opened new possibilities for creating and sharing this music.",
                            "Music festivals and concerts in this genre create community experiences worldwide."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="music_culture",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Musical Instrument: {instrument}",
                    content_template="The {instrument} is a {instrument_type} that produces sound through {sound_method}. It's commonly used in {music_styles} and requires {skills} to master. {historical_note}",
                    tags=["music", "instruments", "{instrument_family}", "learning"],
                    variables={
                        "instrument": [
                            "piano", "guitar", "violin", "drums", "flute", "saxophone", "trumpet",
                            "cello", "clarinet", "bass guitar", "harp", "oboe", "trombone"
                        ],
                        "instrument_type": ["string instrument", "percussion instrument", "wind instrument", "keyboard instrument"],
                        "instrument_family": ["strings", "percussion", "woodwind", "brass", "keyboard"],
                        "sound_method": [
                            "vibrating strings that resonate in a wooden body",
                            "striking surfaces that create rhythmic patterns",
                            "air flowing through tubes and chambers",
                            "keys that trigger hammers hitting strings",
                            "breath creating vibrations in reeds or metal"
                        ],
                        "music_styles": [
                            "classical, jazz, and contemporary music",
                            "rock, blues, and folk traditions",
                            "orchestral and chamber music",
                            "marching bands and jazz ensembles",
                            "world music and cultural celebrations"
                        ],
                        "skills": [
                            "finger dexterity and hand coordination",
                            "breath control and embouchure technique",
                            "rhythm and timing accuracy",
                            "musical theory and ear training",
                            "practice discipline and muscle memory"
                        ],
                        "historical_note": [
                            "This instrument has evolved over centuries with technological improvements.",
                            "Famous composers and musicians have elevated this instrument's reputation.",
                            "The instrument plays important roles in cultural and religious traditions.",
                            "Modern versions incorporate both traditional and innovative design elements.",
                            "Learning this instrument can provide lifelong enjoyment and social opportunities."
                        ]
                    }
                )
            ],
            
            'sports_recreation': [
                DocumentTemplate(
                    category="sports_recreation",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Sport Guide: {sport} Basics",
                    content_template="{sport} is played {gameplay} and requires {skills}. The objective is {objective} and games typically {duration}. {popularity_note}",
                    tags=["sports", "{sport_type}", "recreation", "fitness"],
                    variables={
                        "sport": [
                            "Soccer", "Basketball", "Tennis", "Baseball", "Swimming", "Running",
                            "Cycling", "Golf", "Volleyball", "Hockey", "American Football", "Cricket"
                        ],
                        "sport_type": ["team", "individual", "water", "outdoor", "indoor", "endurance"],
                        "gameplay": [
                            "with two teams trying to score goals",
                            "on a court with hoops and a ball",
                            "on a court with rackets and a net",
                            "on a field with bases and a bat",
                            "in pools or open water"
                        ],
                        "skills": [
                            "coordination, strategy, and teamwork",
                            "agility, accuracy, and quick reflexes",
                            "endurance, technique, and mental focus",
                            "strength, timing, and spatial awareness",
                            "balance, concentration, and practice"
                        ],
                        "objective": [
                            "to score more points than the opponent",
                            "to complete the course in the fastest time",
                            "to win more games or sets",
                            "to achieve the best score or distance",
                            "to demonstrate skill and sportsmanship"
                        ],
                        "duration": [
                            "last 60-90 minutes with breaks",
                            "consist of timed periods or innings",
                            "are played to a certain score",
                            "can vary from minutes to hours",
                            "depend on the competition format"
                        ],
                        "popularity_note": [
                            "This sport is enjoyed by millions of people worldwide as both participants and spectators.",
                            "Professional leagues attract global audiences and create shared cultural experiences.",
                            "The sport provides excellent physical exercise and social interaction opportunities.",
                            "Youth programs help develop character, teamwork, and healthy lifestyle habits.",
                            "Olympic and international competitions showcase the sport's highest level of achievement."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="sports_recreation",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Fitness Fact: {activity} Benefits",
                    content_template="{activity} provides {physical_benefits} and {mental_benefits}. To get started, {beginner_advice} and {equipment_needed}. {motivation_tip}",
                    tags=["fitness", "health", "exercise", "{activity_type}"],
                    variables={
                        "activity": [
                            "Regular walking", "Strength training", "Yoga", "Swimming", "Cycling",
                            "Dancing", "Hiking", "Rock climbing", "Martial arts", "Team sports"
                        ],
                        "activity_type": ["cardio", "strength", "flexibility", "balance", "endurance"],
                        "physical_benefits": [
                            "improved cardiovascular health and muscle strength",
                            "better flexibility and balance",
                            "increased endurance and energy levels",
                            "weight management and bone density",
                            "enhanced immune system function"
                        ],
                        "mental_benefits": [
                            "reduces stress and improves mood through endorphin release",
                            "increases confidence and self-esteem",
                            "provides mental clarity and focus",
                            "offers social interaction and community connections",
                            "creates a sense of accomplishment and goal achievement"
                        ],
                        "beginner_advice": [
                            "start slowly and gradually increase intensity",
                            "focus on proper form rather than speed or weight",
                            "listen to your body and rest when needed",
                            "consider working with a trainer or instructor initially",
                            "set realistic goals and track your progress"
                        ],
                        "equipment_needed": [
                            "minimal equipment is required to begin",
                            "basic gear can be purchased affordably",
                            "many exercises can be done with body weight only",
                            "community centers often provide access to facilities",
                            "online resources offer guidance for home workouts"
                        ],
                        "motivation_tip": [
                            "Find an activity you genuinely enjoy to maintain long-term consistency.",
                            "Exercise with friends or join groups to make it more social and fun.",
                            "Focus on how good you feel after exercise rather than just physical changes.",
                            "Celebrate small victories and progress milestones along the way.",
                            "Remember that any movement is better than no movement at all."
                        ]
                    }
                )
            ],
            
            'food_culture': [
                DocumentTemplate(
                    category="food_culture",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Cuisine Guide: {cuisine} Food",
                    content_template="{cuisine} cuisine is known for {characteristics} and commonly uses {ingredients}. Popular dishes include {dish_examples} and the cooking style {cooking_style}. {cultural_significance}",
                    tags=["food", "culture", "cuisine", "{region}"],
                    variables={
                        "cuisine": [
                            "Italian", "Chinese", "Mexican", "Japanese", "Indian", "French",
                            "Thai", "Mediterranean", "Korean", "Middle Eastern", "American", "Brazilian"
                        ],
                        "region": ["european", "asian", "american", "african", "oceanic"],
                        "characteristics": [
                            "fresh ingredients and simple preparation methods",
                            "bold spices and complex flavor combinations",
                            "emphasis on seasonal and local produce",
                            "balanced nutrition and artistic presentation",
                            "comfort foods and hearty portions"
                        ],
                        "ingredients": [
                            "herbs, olive oil, tomatoes, and cheese",
                            "soy sauce, ginger, garlic, and rice",
                            "chilies, lime, cilantro, and corn",
                            "fish, seaweed, miso, and vegetables",
                            "spices, lentils, rice, and yogurt"
                        ],
                        "dish_examples": [
                            "pasta dishes, pizza, and risotto",
                            "stir-fries, dumplings, and noodle soups",
                            "tacos, enchiladas, and salsas",
                            "sushi, tempura, and miso soup",
                            "curries, flatbreads, and chutneys"
                        ],
                        "cooking_style": [
                            "emphasizes technique and quality ingredients",
                            "balances flavors, textures, and colors",
                            "incorporates family recipes and traditions",
                            "adapts to local ingredients and preferences",
                            "celebrates communal dining and social eating"
                        ],
                        "cultural_significance": [
                            "Food traditions connect generations and preserve cultural identity.",
                            "Meals often serve as important social and family bonding times.",
                            "Culinary practices reflect geography, climate, and available resources.",
                            "Food festivals and celebrations showcase cultural pride and heritage.",
                            "Modern fusion cooking creates exciting new flavor combinations and experiences."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="food_culture",
                    intent=UserIntent.INSTRUCTION_REQUEST,
                    title_template="Cooking Tip: {cooking_topic}",
                    content_template="When {cooking_topic}, {technique} works best because {reason}. {helpful_tip} Remember to {safety_advice} for best results. {chef_secret}",
                    tags=["cooking", "tips", "kitchen", "food-prep"],
                    variables={
                        "cooking_topic": [
                            "preparing vegetables", "cooking pasta", "grilling meat", "baking bread",
                            "making sauces", "seasoning food", "food storage", "meal planning"
                        ],
                        "technique": [
                            "cutting vegetables uniformly",
                            "using plenty of salted boiling water",
                            "preheating the grill and oiling the grates",
                            "measuring ingredients accurately",
                            "tasting and adjusting as you cook"
                        ],
                        "reason": [
                            "it ensures even cooking and better presentation",
                            "it prevents sticking and improves texture",
                            "it creates proper searing and prevents sticking",
                            "baking requires precision for proper chemical reactions",
                            "flavors need time to develop and balance"
                        ],
                        "helpful_tip": [
                            "Keep your knives sharp for easier and safer cutting.",
                            "Save some pasta water to help bind sauces.",
                            "Let meat rest after grilling to redistribute juices.",
                            "Use a kitchen scale for more accurate measurements.",
                            "Taste your food throughout the cooking process."
                        ],
                        "safety_advice": [
                            "wash your hands and clean surfaces frequently",
                            "keep hot foods hot and cold foods cold",
                            "use separate cutting boards for raw meat",
                            "check internal temperatures with a thermometer",
                            "store leftovers promptly in the refrigerator"
                        ],
                        "chef_secret": [
                            "Professional chefs always prep all ingredients before starting to cook.",
                            "High-quality ingredients make the biggest difference in final taste.",
                            "Seasoning in layers throughout cooking builds deeper flavors.",
                            "Patience is often the most important ingredient in great cooking.",
                            "Learning to cook is a lifelong journey of discovery and enjoyment."
                        ]
                    }
                )
            ],
            
            'technology_trends': [
                DocumentTemplate(
                    category="technology_trends",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Tech Trend: {technology} Impact",
                    content_template="{technology} is {description} that {current_use} and {future_potential}. This technology {benefits} but also {challenges}. {adoption_timeline}",
                    tags=["technology", "trends", "innovation", "future"],
                    variables={
                        "technology": [
                            "Artificial Intelligence", "Virtual Reality", "Blockchain", "Internet of Things",
                            "5G Networks", "Electric Vehicles", "Renewable Energy", "Biotechnology",
                            "Quantum Computing", "Augmented Reality", "3D Printing", "Robotics"
                        ],
                        "description": [
                            "an emerging technology",
                            "a revolutionary innovation",
                            "a transformative development",
                            "a cutting-edge advancement",
                            "a game-changing technology"
                        ],
                        "current_use": [
                            "is already being used in various industries",
                            "has early applications in specialized fields",
                            "is being tested in pilot programs",
                            "has consumer applications available today",
                            "is being integrated into existing systems"
                        ],
                        "future_potential": [
                            "promises to revolutionize how we work and live",
                            "could solve major global challenges",
                            "will create new industries and job opportunities",
                            "may fundamentally change social interactions",
                            "has the potential to improve quality of life significantly"
                        ],
                        "benefits": [
                            "increases efficiency and reduces costs",
                            "provides new capabilities and experiences",
                            "improves accessibility and convenience",
                            "enables better decision-making through data",
                            "creates opportunities for innovation and creativity"
                        ],
                        "challenges": [
                            "raises questions about privacy and security",
                            "requires new skills and education",
                            "may disrupt existing jobs and industries",
                            "needs careful regulation and ethical considerations",
                            "faces technical limitations and implementation costs"
                        ],
                        "adoption_timeline": [
                            "Widespread adoption is expected within the next 5-10 years.",
                            "Early adopters are already seeing benefits, with broader use coming soon.",
                            "The technology is still developing but shows promising results.",
                            "Major companies are investing heavily in research and development.",
                            "Consumer awareness and acceptance are growing rapidly."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="technology_trends", 
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Digital Life: {digital_aspect}",
                    content_template="Modern {digital_aspect} involves {activities} and has {impact_type} on daily life. People use {tools} to {purposes} and {social_change}. {digital_wellness_tip}",
                    tags=["technology", "digital-life", "social-media", "modern-life"],
                    variables={
                        "digital_aspect": [
                            "social media usage", "online shopping", "remote work", "digital entertainment",
                            "mobile communication", "cloud storage", "online learning", "digital payments"
                        ],
                        "activities": [
                            "sharing content, connecting with others, and staying informed",
                            "browsing products, comparing prices, and making purchases",
                            "collaborating on projects and attending virtual meetings",
                            "streaming content and playing interactive games",
                            "instant messaging and video calling"
                        ],
                        "impact_type": [
                            "both positive and negative effects",
                            "significant transformative effects",
                            "mostly positive but some concerning effects",
                            "revolutionary changes",
                            "gradual but profound effects"
                        ],
                        "tools": [
                            "smartphones, tablets, and computers",
                            "apps, websites, and digital platforms",
                            "cloud services and online tools",
                            "social networks and communication apps",
                            "digital assistants and smart devices"
                        ],
                        "purposes": [
                            "stay connected with friends and family",
                            "access information and entertainment",
                            "manage work and personal tasks",
                            "shop, learn, and explore interests",
                            "create content and express creativity"
                        ],
                        "social_change": [
                            "This has changed how we form and maintain relationships.",
                            "Digital skills have become essential for participation in modern society.",
                            "New forms of community and social interaction have emerged.",
                            "The line between online and offline life continues to blur.",
                            "Digital literacy is now as important as traditional literacy."
                        ],
                        "digital_wellness_tip": [
                            "Balance screen time with offline activities for mental and physical health.",
                            "Be mindful about privacy settings and what personal information you share.",
                            "Take regular breaks from devices to prevent eye strain and mental fatigue.",
                            "Cultivate real-world relationships alongside digital connections.",
                            "Stay informed about technology trends while maintaining critical thinking skills."
                        ]
                    }
                )
            ],
            
            'lifestyle_wellness': [
                DocumentTemplate(
                    category="lifestyle_wellness",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Wellness Topic: {wellness_area}",
                    content_template="{wellness_area} involves {practices} and can {benefits}. Research shows that {scientific_backing} and {implementation_tips}. {holistic_note}",
                    tags=["wellness", "health", "lifestyle", "self-care"],
                    variables={
                        "wellness_area": [
                            "Stress management", "Sleep hygiene", "Mindfulness meditation", "Work-life balance",
                            "Social connections", "Mental health", "Nutrition", "Exercise", "Time management"
                        ],
                        "practices": [
                            "regular exercise, healthy eating, and adequate rest",
                            "meditation, deep breathing, and mindful activities",
                            "setting boundaries and prioritizing self-care",
                            "building supportive relationships and community connections",
                            "developing healthy routines and positive habits"
                        ],
                        "benefits": [
                            "reduce anxiety and improve overall mood",
                            "increase energy levels and mental clarity",
                            "strengthen immune system and physical health",
                            "improve relationships and social satisfaction",
                            "enhance productivity and life satisfaction"
                        ],
                        "scientific_backing": [
                            "these practices activate the body's natural healing responses",
                            "regular practice leads to measurable changes in brain chemistry",
                            "consistent healthy habits reduce risk of chronic diseases",
                            "social connections are linked to increased longevity",
                            "small daily improvements compound into significant long-term benefits"
                        ],
                        "implementation_tips": [
                            "Start small with just 5-10 minutes daily and gradually increase",
                            "Find activities you genuinely enjoy to ensure long-term consistency",
                            "Track your progress to stay motivated and see improvements",
                            "Join groups or find accountability partners for support",
                            "Be patient with yourself as new habits take time to develop"
                        ],
                        "holistic_note": [
                            "Remember that wellness is a journey, not a destination, and everyone's path is unique.",
                            "Physical, mental, and emotional health are interconnected and support each other.",
                            "Small, consistent changes often create more lasting results than dramatic overhauls.",
                            "Professional guidance can be helpful for addressing specific health concerns.",
                            "Wellness practices should enhance your life, not create additional stress or pressure."
                        ]
                    }
                )
            ],
            
            # DEVICE TECHNOLOGY EXPERTISE
            'device_technology': [
                DocumentTemplate(
                    category="device_technology",
                    intent=UserIntent.TROUBLESHOOTING,
                    title_template="Device Help: {device_issue} on {device_type}",
                    content_template="When experiencing {device_issue} on your {device_type}, {diagnostic_step}. Common causes include {common_causes} and {solution_approach}. {prevention_tip}",
                    tags=["devices", "troubleshooting", "{device_type}", "technology"],
                    variables={
                        "device_type": [
                            "iPhone", "Android phone", "Windows laptop", "MacBook", "iPad", 
                            "Android tablet", "desktop computer", "smart TV", "gaming console"
                        ],
                        "device_issue": [
                            "slow performance", "battery draining quickly", "app crashes",
                            "connectivity problems", "storage space warnings", "overheating",
                            "screen flickering", "audio issues", "charging problems"
                        ],
                        "diagnostic_step": [
                            "first restart your device to clear temporary files",
                            "check available storage space and free up memory if needed",
                            "ensure your software is updated to the latest version",
                            "close unnecessary background apps to improve performance",
                            "verify your internet connection is stable and working"
                        ],
                        "common_causes": [
                            "too many apps running simultaneously",
                            "outdated software or operating system",
                            "insufficient storage space",
                            "background processes consuming resources",
                            "hardware components reaching end of life"
                        ],
                        "solution_approach": [
                            "the solution often involves clearing caches and restarting",
                            "updating software usually resolves compatibility issues",
                            "freeing up storage space can significantly improve performance",
                            "adjusting settings can optimize battery life and speed",
                            "professional repair may be needed for hardware problems"
                        ],
                        "prevention_tip": [
                            "Regular maintenance includes restarting weekly and updating software promptly.",
                            "Monitor storage usage and delete unused apps and files regularly.",
                            "Use original chargers and avoid extreme temperatures to preserve battery life.",
                            "Install apps only from official stores to avoid malware and performance issues.",
                            "Back up important data regularly to prevent loss during troubleshooting."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="device_technology",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Device Setup: {setup_task} for {device_category}",
                    content_template="Setting up {setup_task} on {device_category} involves {setup_process}. The key steps are {important_steps} and {configuration_tips}. {optimization_advice}",
                    tags=["setup", "configuration", "devices", "{device_category}"],
                    variables={
                        "device_category": [
                            "smartphones", "computers", "tablets", "smart home devices",
                            "gaming systems", "streaming devices", "wearables"
                        ],
                        "setup_task": [
                            "initial configuration", "security settings", "backup systems",
                            "app installation", "account synchronization", "privacy controls",
                            "parental controls", "accessibility features"
                        ],
                        "setup_process": [
                            "following a systematic approach to ensure everything works properly",
                            "configuring essential settings before adding personal content",
                            "prioritizing security and privacy during the initial setup",
                            "connecting to necessary services and accounts securely"
                        ],
                        "important_steps": [
                            "creating strong passwords and enabling two-factor authentication",
                            "reviewing privacy settings and adjusting sharing preferences",
                            "setting up automatic backups to protect your data",
                            "installing essential security updates before using the device"
                        ],
                        "configuration_tips": [
                            "take time to explore settings menus to customize your experience",
                            "document important passwords and settings in a secure location",
                            "test all features before relying on them for important tasks",
                            "research recommended apps and settings for your specific use case"
                        ],
                        "optimization_advice": [
                            "Disable unnecessary notifications to reduce distractions and save battery.",
                            "Organize apps and files logically to improve productivity and efficiency.",
                            "Set up regular maintenance routines to keep your device running smoothly.",
                            "Learn keyboard shortcuts and gestures to use your device more effectively.",
                            "Consider your workflow and customize settings to match your daily habits."
                        ]
                    }
                )
            ],
            
            # WIFI & NETWORKING EXPERTISE
            'wifi_networking': [
                DocumentTemplate(
                    category="wifi_networking",
                    intent=UserIntent.TROUBLESHOOTING,
                    title_template="WiFi Fix: {wifi_problem} Troubleshooting",
                    content_template="When experiencing {wifi_problem}, {diagnostic_approach}. Check {check_items} and try {solution_steps}. {advanced_tip}",
                    tags=["wifi", "networking", "troubleshooting", "internet"],
                    variables={
                        "wifi_problem": [
                            "slow internet speeds", "frequent disconnections", "cannot connect to network",
                            "weak signal strength", "intermittent connectivity", "no internet access",
                            "network not showing up", "password not working", "limited connectivity"
                        ],
                        "diagnostic_approach": [
                            "start by checking if the issue affects all devices or just one",
                            "determine if the problem is with your device, router, or internet service",
                            "test connectivity at different locations in your home or office",
                            "verify whether other online services are working normally"
                        ],
                        "check_items": [
                            "router power and cable connections",
                            "device WiFi settings and saved networks",
                            "interference from other electronic devices",
                            "router placement and antenna orientation",
                            "internet service provider status and outages"
                        ],
                        "solution_steps": [
                            "restarting your router and modem for 30 seconds",
                            "forgetting and reconnecting to the WiFi network",
                            "moving closer to the router to test signal strength",
                            "updating device WiFi drivers and router firmware",
                            "changing WiFi channels to avoid interference"
                        ],
                        "advanced_tip": [
                            "For persistent issues, consider upgrading to a mesh network system for better coverage.",
                            "Use WiFi analyzer apps to identify the best channels and optimal router placement.",
                            "Quality of Service (QoS) settings can prioritize important traffic for better performance.",
                            "Ethernet connections provide more stable speeds for devices that need consistent bandwidth.",
                            "Regular router reboots and firmware updates prevent many common connectivity issues."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="wifi_networking",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Network Setup: {network_topic} Guide",
                    content_template="Understanding {network_topic} involves {basic_concept}. The process includes {setup_elements} and {best_practices}. {security_note}",
                    tags=["networking", "setup", "wifi", "security"],
                    variables={
                        "network_topic": [
                            "home WiFi optimization", "router configuration", "network security",
                            "guest network setup", "port forwarding", "VPN configuration",
                            "mesh network installation", "bandwidth management"
                        ],
                        "basic_concept": [
                            "configuring your network equipment for optimal performance and security",
                            "balancing speed, coverage, and security for your specific needs",
                            "understanding how different devices and services use your network",
                            "creating a reliable foundation for all your connected devices"
                        ],
                        "setup_elements": [
                            "choosing appropriate network names and strong passwords",
                            "configuring security protocols and access controls",
                            "optimizing channel selection and transmission power",
                            "setting up Quality of Service rules for different devices"
                        ],
                        "best_practices": [
                            "regularly updating firmware and monitoring network performance",
                            "using WPA3 security when available, or WPA2 as a minimum",
                            "creating separate networks for guests and IoT devices",
                            "documenting your configuration for future reference and troubleshooting"
                        ],
                        "security_note": [
                            "Always change default passwords and disable WPS for better security.",
                            "Monitor connected devices regularly and remove unknown or unused connections.",
                            "Keep router firmware updated to protect against security vulnerabilities.",
                            "Consider using a firewall and disabling unnecessary services to reduce attack surface.",
                            "Guest networks prevent visitors from accessing your main network and devices."
                        ]
                    }
                )
            ],
            
            # SECURITY & PRIVACY EXPERTISE
            'security_privacy': [
                DocumentTemplate(
                    category="security_privacy",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Security Guide: {security_topic} Protection",
                    content_template="Protecting against {security_topic} requires {protection_approach}. Key strategies include {security_measures} and {implementation_steps}. {warning_signs}",
                    tags=["security", "privacy", "cybersecurity", "{security_topic}"],
                    variables={
                        "security_topic": [
                            "phishing attacks", "malware infections", "data breaches",
                            "identity theft", "social engineering", "ransomware",
                            "password attacks", "public WiFi risks", "online scams"
                        ],
                        "protection_approach": [
                            "a multi-layered security strategy with strong authentication",
                            "staying informed about current threats and attack methods",
                            "implementing proven security practices consistently",
                            "maintaining updated software and security tools"
                        ],
                        "security_measures": [
                            "using strong, unique passwords with two-factor authentication",
                            "keeping software updated and running reputable antivirus protection",
                            "being cautious with email attachments and suspicious links",
                            "regularly backing up important data to secure locations",
                            "limiting personal information shared on social media platforms"
                        ],
                        "implementation_steps": [
                            "start with password manager setup and enable 2FA on important accounts",
                            "configure automatic updates for operating systems and critical software",
                            "learn to recognize common phishing and social engineering tactics",
                            "establish regular backup routines and test recovery procedures"
                        ],
                        "warning_signs": [
                            "Watch for unexpected password reset emails, unusual account activity, or unfamiliar charges.",
                            "Be suspicious of urgent requests for personal information, even from known contacts.",
                            "Slow computer performance or unexpected pop-ups may indicate malware infection.",
                            "Emails with poor grammar, generic greetings, or pressure tactics are often scams.",
                            "Legitimate companies rarely ask for sensitive information via email or phone calls."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="security_privacy",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Privacy Control: {privacy_area} Management",
                    content_template="Managing {privacy_area} involves {privacy_approach}. Essential controls include {privacy_settings} and {monitoring_practices}. {privacy_tip}",
                    tags=["privacy", "data-protection", "online-safety", "{privacy_area}"],
                    variables={
                        "privacy_area": [
                            "social media privacy", "online tracking", "data collection",
                            "location sharing", "personal information", "digital footprint",
                            "browser privacy", "mobile app permissions", "cloud storage security"
                        ],
                        "privacy_approach": [
                            "understanding what information you're sharing and with whom",
                            "regularly reviewing and adjusting privacy settings across all platforms",
                            "using privacy-focused tools and services when possible",
                            "staying informed about data collection practices and your rights"
                        ],
                        "privacy_settings": [
                            "limiting public visibility of personal posts and information",
                            "disabling location tracking for non-essential apps and services",
                            "opting out of data collection and targeted advertising when possible",
                            "using private browsing modes and clearing cookies regularly"
                        ],
                        "monitoring_practices": [
                            "regularly checking what information is publicly visible about you",
                            "reviewing app permissions and revoking unnecessary access",
                            "monitoring your accounts for unusual activity or unauthorized access",
                            "periodically searching for your name online to see what information is available"
                        ],
                        "privacy_tip": [
                            "Use different email addresses for different purposes to limit data correlation.",
                            "Read privacy policies for services you use, especially focusing on data sharing practices.",
                            "Consider using VPNs for additional privacy, especially on public networks.",
                            "Regularly clean up old accounts and services you no longer use.",
                            "Be mindful of what you share in photos, including background details and metadata."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="security_privacy",
                    intent=UserIntent.TROUBLESHOOTING,
                    title_template="Security Incident: {incident_type} Response",
                    content_template="If you suspect {incident_type}, {immediate_action}. Next steps include {investigation_steps} and {recovery_measures}. {prevention_future}",
                    tags=["security", "incident-response", "recovery", "{incident_type}"],
                    variables={
                        "incident_type": [
                            "account compromise", "malware infection", "data breach",
                            "identity theft", "credit card fraud", "email hack",
                            "social media breach", "ransomware attack", "phone scam"
                        ],
                        "immediate_action": [
                            "immediately change passwords for affected accounts and enable 2FA",
                            "disconnect from the internet to prevent further damage",
                            "contact your bank or credit card company to report suspicious activity",
                            "scan your devices with updated antivirus software",
                            "document what happened and preserve evidence if needed"
                        ],
                        "investigation_steps": [
                            "checking recent account activity and login history",
                            "reviewing bank and credit card statements for unauthorized transactions",
                            "running comprehensive malware scans on all devices",
                            "checking if your information appears in known data breaches"
                        ],
                        "recovery_measures": [
                            "updating all passwords and security questions",
                            "contacting relevant companies and agencies to report the incident",
                            "monitoring credit reports and considering fraud alerts",
                            "restoring data from clean backups if necessary"
                        ],
                        "prevention_future": [
                            "To prevent future incidents, implement stronger security practices immediately.",
                            "Regular security audits and password updates can catch problems early.",
                            "Education about current threats helps you recognize attacks before they succeed.",
                            "Consider professional help for serious incidents or if you're unsure about recovery.",
                            "Document lessons learned and update your security procedures accordingly."
                        ]
                    }
                )
            ],
            
            # DIAGNOSTIC & TROUBLESHOOTING EXPERTISE
            'diagnostic_troubleshooting': [
                DocumentTemplate(
                    category="diagnostic_troubleshooting",
                    intent=UserIntent.TROUBLESHOOTING,
                    title_template="Diagnosis: {problem_type} Analysis",
                    content_template="When diagnosing {problem_type}, {diagnostic_method}. Start by {initial_steps} and then {systematic_approach}. {expert_tip}",
                    tags=["diagnostics", "troubleshooting", "problem-solving", "{problem_type}"],
                    variables={
                        "problem_type": [
                            "system performance issues", "software crashes", "hardware failures",
                            "network connectivity problems", "application errors", "startup issues",
                            "audio/video problems", "printing difficulties", "file corruption"
                        ],
                        "diagnostic_method": [
                            "follow a systematic approach to isolate the root cause",
                            "gather information about when and how the problem occurs",
                            "test different scenarios to reproduce the issue consistently",
                            "eliminate potential causes one by one through testing"
                        ],
                        "initial_steps": [
                            "documenting exactly what happens and any error messages",
                            "noting recent changes to software, hardware, or settings",
                            "checking if the problem affects other users or devices",
                            "verifying basic connectivity and power connections"
                        ],
                        "systematic_approach": [
                            "test with minimal configuration to identify conflicting software",
                            "check system logs and error reports for specific clues",
                            "try the same task in safe mode or with a different user account",
                            "use built-in diagnostic tools and system health monitors"
                        ],
                        "expert_tip": [
                            "Keep a troubleshooting log to track what you've tried and the results.",
                            "Search for specific error codes and messages to find targeted solutions.",
                            "Before making changes, create system restore points or backups.",
                            "Sometimes the simplest solutions (like restarting) work best - try them first.",
                            "Don't hesitate to seek professional help for critical systems or persistent issues."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="diagnostic_troubleshooting",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Method: {troubleshooting_approach} Techniques",
                    content_template="The {troubleshooting_approach} approach involves {methodology_description}. Key principles include {core_principles} and {practical_application}. {success_factors}",
                    tags=["methodology", "problem-solving", "troubleshooting", "systematic"],
                    variables={
                        "troubleshooting_approach": [
                            "divide and conquer", "process of elimination", "root cause analysis",
                            "systematic debugging", "layered diagnostics", "comparative testing",
                            "incremental isolation", "pattern recognition"
                        ],
                        "methodology_description": [
                            "breaking complex problems into smaller, manageable components",
                            "systematically testing and eliminating potential causes",
                            "following logical steps to identify the underlying issue",
                            "using structured methods to ensure nothing is overlooked"
                        ],
                        "core_principles": [
                            "gathering complete information before making assumptions",
                            "testing only one variable at a time to isolate causes",
                            "documenting all steps and results for future reference",
                            "working from simple to complex solutions progressively"
                        ],
                        "practical_application": [
                            "start with the most common causes before exploring unusual scenarios",
                            "use comparison testing with known working systems when possible",
                            "verify your fixes actually solve the problem and don't create new ones",
                            "consider both hardware and software factors in your analysis"
                        ],
                        "success_factors": [
                            "Patience and persistence are essential - complex problems take time to solve.",
                            "Good documentation helps you avoid repeating unsuccessful attempts.",
                            "Understanding normal behavior helps you recognize abnormal patterns.",
                            "Collaboration with others can provide fresh perspectives and solutions.",
                            "Learning from each troubleshooting experience improves future problem-solving."
                        ]
                    }
                )
            ],
            
            # EDUCATIONAL CONTENT & TUTORING
            'educational_tutoring': [
                DocumentTemplate(
                    category="educational_tutoring",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Study Method: {study_technique} for {subject_area}",
                    content_template="The {study_technique} method is {technique_description} that {benefits}. To implement this, {implementation_steps} and {optimization_tips}. {success_indicator}",
                    tags=["education", "study-methods", "learning", "{subject_area}"],
                    variables={
                        "study_technique": [
                            "spaced repetition", "active recall", "the Feynman technique",
                            "mind mapping", "the Pomodoro technique", "elaborative interrogation",
                            "dual coding", "interleaving practice", "self-explanation"
                        ],
                        "subject_area": [
                            "mathematics", "sciences", "languages", "history",
                            "literature", "programming", "test preparation", "skill acquisition"
                        ],
                        "technique_description": [
                            "a research-backed learning strategy",
                            "a cognitive approach to information processing",
                            "a systematic method for improving retention",
                            "an active learning technique that engages multiple mental processes"
                        ],
                        "benefits": [
                            "significantly improves long-term retention and understanding",
                            "helps identify gaps in knowledge more effectively",
                            "makes studying more efficient and less time-consuming",
                            "builds deeper comprehension rather than surface-level memorization"
                        ],
                        "implementation_steps": [
                            "break material into small, manageable chunks",
                            "schedule regular review sessions at increasing intervals",
                            "test yourself frequently without looking at notes",
                            "explain concepts in your own words as if teaching someone else"
                        ],
                        "optimization_tips": [
                            "use multiple senses when possible - visual, auditory, and kinesthetic",
                            "connect new information to existing knowledge and personal experiences",
                            "vary your study environment and times to strengthen memory formation",
                            "take regular breaks to allow your brain to consolidate information"
                        ],
                        "success_indicator": [
                            "You'll know it's working when you can explain concepts clearly without notes.",
                            "Success shows as improved performance on practice tests and reduced study anxiety.",
                            "The technique is effective when you retain information longer with less effort.",
                            "Look for increased confidence and faster problem-solving in the subject area.",
                            "Progress appears as the ability to apply knowledge to new, unfamiliar problems."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="educational_tutoring",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Learning Support: {learning_topic} Guidance",
                    content_template="Understanding {learning_topic} involves {learning_approach}. Key strategies include {effective_methods} and {common_challenges}. {motivation_tip}",
                    tags=["tutoring", "academic-support", "learning", "{learning_topic}"],
                    variables={
                        "learning_topic": [
                            "test anxiety management", "note-taking strategies", "time management for students",
                            "reading comprehension", "mathematical problem-solving", "essay writing",
                            "research skills", "critical thinking", "memorization techniques"
                        ],
                        "learning_approach": [
                            "developing personalized strategies that match your learning style",
                            "building foundational skills before tackling advanced concepts",
                            "creating structured study routines and tracking progress",
                            "understanding the underlying principles rather than memorizing facts"
                        ],
                        "effective_methods": [
                            "breaking complex tasks into smaller, achievable goals",
                            "using multiple learning modalities to reinforce understanding",
                            "practicing regularly in low-stakes environments before major assessments",
                            "seeking feedback early and often to correct misunderstandings"
                        ],
                        "common_challenges": [
                            "Students often struggle with perfectionism and fear of making mistakes",
                            "Information overload can occur when trying to learn too much too quickly",
                            "Lack of motivation may develop when progress seems slow or unclear",
                            "Poor time management often leads to cramming and increased stress levels"
                        ],
                        "motivation_tip": [
                            "Celebrate small wins and progress milestones to maintain momentum.",
                            "Connect learning goals to personal interests and future aspirations.",
                            "Study with others when possible - collaboration can increase engagement.",
                            "Remember that struggle and confusion are normal parts of the learning process.",
                            "Focus on growth and improvement rather than comparing yourself to others."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="educational_tutoring",
                    intent=UserIntent.EXPLANATION_REQUEST,
                    title_template="Academic Skill: {academic_skill} Development",
                    content_template="Developing {academic_skill} requires {skill_development_approach}. Essential components include {skill_components} and {practice_methods}. {mastery_indicator}",
                    tags=["academic-skills", "education", "skill-development", "{academic_skill}"],
                    variables={
                        "academic_skill": [
                            "critical thinking", "research methodology", "academic writing",
                            "data analysis", "presentation skills", "collaborative learning",
                            "information literacy", "problem-solving", "metacognitive awareness"
                        ],
                        "skill_development_approach": [
                            "consistent practice with gradually increasing complexity",
                            "learning from examples and models of excellent work",
                            "receiving constructive feedback and reflection on performance",
                            "applying the skill across different contexts and subjects"
                        ],
                        "skill_components": [
                            "understanding the fundamental principles and theories",
                            "developing practical techniques and systematic approaches",
                            "building confidence through successful application",
                            "learning to evaluate and improve your own performance"
                        ],
                        "practice_methods": [
                            "start with guided practice before attempting independent work",
                            "use real-world applications to make learning more meaningful",
                            "practice in a variety of contexts to build transferable skills",
                            "seek feedback from instructors, peers, and self-assessment"
                        ],
                        "mastery_indicator": [
                            "Mastery shows when you can apply the skill flexibly in new situations.",
                            "You've developed competence when you can teach the skill to others effectively.",
                            "Success appears as increased speed and accuracy with less conscious effort.",
                            "Proficiency is evident when you can adapt the skill to unexpected challenges.",
                            "Expertise develops when you can innovate and improve upon established methods."
                        ]
                    }
                )
            ],
            
            # TRIVIA & FUN FACTS
            'trivia_facts': [
                DocumentTemplate(
                    category="trivia_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Fun Fact: {fact_category} Trivia",
                    content_template="Did you know that {amazing_fact}? This {fact_type} demonstrates {significance} and {why_interesting}. {additional_detail}",
                    tags=["trivia", "fun-facts", "{fact_category}", "interesting"],
                    variables={
                        "fact_category": [
                            "animal behavior", "space and astronomy", "human body",
                            "technology and inventions", "history and culture", "science and nature",
                            "language and words", "geography and places", "food and cooking"
                        ],
                        "amazing_fact": [
                            "honey never spoils - archaeologists have found edible honey in ancient Egyptian tombs",
                            "octopuses have three hearts and blue blood",
                            "a group of flamingos is called a 'flamboyance'",
                            "bananas are berries, but strawberries aren't",
                            "the human brain generates about 12-25 watts of electricity",
                            "there are more possible games of chess than atoms in the observable universe",
                            "sharks have been around longer than trees",
                            "a day on Venus is longer than its year",
                            "the dot over a lowercase 'i' or 'j' is called a tittle"
                        ],
                        "fact_type": [
                            "fascinating phenomenon",
                            "surprising scientific discovery",
                            "remarkable natural occurrence",
                            "unexpected historical detail",
                            "intriguing biological adaptation"
                        ],
                        "significance": [
                            "how complex and amazing our natural world truly is",
                            "the incredible diversity of life and adaptation strategies",
                            "how much we still have to learn about the universe",
                            "the interconnectedness of seemingly unrelated phenomena",
                            "how science continues to surprise us with new discoveries"
                        ],
                        "why_interesting": [
                            "it challenges our common assumptions about how things work",
                            "it shows the incredible creativity of evolution and natural processes",
                            "it demonstrates the power of scientific observation and discovery",
                            "it connects different fields of knowledge in unexpected ways",
                            "it reminds us that the world is full of wonder and mystery"
                        ],
                        "additional_detail": [
                            "Scientists are still studying this phenomenon to understand all its implications.",
                            "This discovery has led to new research questions and technological applications.",
                            "Many cultures have known about this for centuries, but science has only recently explained why.",
                            "This fact has practical applications in fields ranging from medicine to engineering.",
                            "Learning about things like this can inspire curiosity and appreciation for the natural world."
                        ]
                    }
                ),
                DocumentTemplate(
                    category="trivia_facts",
                    intent=UserIntent.FACTUAL_QUERY,
                    title_template="Quiz Question: {quiz_topic} Challenge",
                    content_template="Here's a {quiz_difficulty} question about {quiz_topic}: {question_text} The answer is {correct_answer}, and {explanation}. {fun_connection}",
                    tags=["quiz", "trivia", "challenge", "{quiz_topic}"],
                    variables={
                        "quiz_topic": [
                            "world geography", "scientific discoveries", "historical events",
                            "literature and books", "movies and entertainment", "sports facts",
                            "technology milestones", "art and culture", "nature and wildlife"
                        ],
                        "quiz_difficulty": [
                            "beginner-friendly", "intermediate", "challenging", "expert-level"
                        ],
                        "question_text": [
                            "What is the smallest country in the world?",
                            "Which planet has the most moons in our solar system?",
                            "What year did the first iPhone launch?",
                            "Which mammal is known to have the most powerful bite?",
                            "What is the chemical symbol for gold?",
                            "Which Shakespeare play features the character Iago?",
                            "What is the tallest mountain in Africa?",
                            "Which programming language was named after a comedy group?"
                        ],
                        "correct_answer": [
                            "Vatican City (0.17 square miles)",
                            "Saturn (with 146 confirmed moons)",
                            "2007 (announced by Steve Jobs)",
                            "the hippopotamus (1,800 PSI bite force)",
                            "Au (from the Latin 'aurum')",
                            "Othello (Iago is the main antagonist)",
                            "Mount Kilimanjaro (19,341 feet)",
                            "Python (named after Monty Python)"
                        ],
                        "explanation": [
                            "this makes it smaller than most city parks",
                            "Jupiter was previously thought to have the most, but recent discoveries have given Saturn the lead",
                            "it revolutionized the smartphone industry and mobile computing",
                            "despite their herbivorous diet, hippos are considered very dangerous",
                            "gold has been valued by civilizations for thousands of years",
                            "the play explores themes of jealousy, betrayal, and manipulation",
                            "it's actually a volcanic mountain with three peaks",
                            "the creators wanted a short, unique name and were fans of the comedy group"
                        ],
                        "fun_connection": [
                            "This connects to broader topics like political geography and international relations.",
                            "Space exploration continues to reveal new discoveries about our solar system.",
                            "Technology history shows how quickly innovation can change our daily lives.",
                            "Nature often surprises us with unexpected facts about familiar animals.",
                            "The history of science and discovery spans many cultures and time periods.",
                            "Literature reflects the human condition across different eras and societies.",
                            "Geography and geology tell the story of our planet's formation and changes.",
                            "Programming culture often includes humor and references to popular culture."
                        ]
                    }
                )
            ]
        }
        
        return expanded_templates
    
    def get_category_descriptions(self) -> Dict[str, str]:
        """Get descriptions for all available categories"""
        return {
            # Original technical categories
            "mathematical_calculation": "Math problems, equations, and numerical concepts",
            "code_debugging": "Programming errors, troubleshooting, and technical solutions",
            "explanation_request": "Technical concepts, how-things-work, and educational content",
            "casual_conversation": "Greetings, friendly responses, and social interactions",
            "creative_writing": "Stories, creative content, and imaginative scenarios",
            
            # New general knowledge categories
            "historical_facts": "World history, civilizations, and historical events",
            "science_facts": "Natural phenomena, animals, physics, chemistry, and biology",
            "geography_facts": "World locations, countries, landmarks, and geographical features",
            
            # New pop culture categories  
            "movies_tv": "Entertainment, films, television, and media industry",
            "music_culture": "Musical genres, instruments, artists, and cultural impact",
            "sports_recreation": "Sports, fitness, games, and recreational activities",
            "food_culture": "Cuisines, cooking, recipes, and food traditions",
            "technology_trends": "Modern technology, digital life, and innovation",
            "lifestyle_wellness": "Health, wellness, self-care, and life improvement",
            
            # Device & Technology Expertise
            "device_technology": "Device troubleshooting, setup, and optimization",
            "wifi_networking": "WiFi, networking, and connectivity solutions",
            "security_privacy": "Cybersecurity, privacy protection, and incident response",
            "diagnostic_troubleshooting": "Systematic problem-solving and diagnostic methods",
            "educational_tutoring": "Study techniques, learning methods, and academic support",
            "trivia_facts": "Fun facts, trivia, and entertaining educational content"
        }
    
    def get_generation_estimates(self) -> Dict[str, Dict[str, Any]]:
        """Get cost and time estimates for each category"""
        base_cost_per_doc = 0.0028
        base_time_per_doc = 2.6  # seconds
        
        category_counts = {
            # Technical (original)
            "mathematical_calculation": 500,
            "code_debugging": 750, 
            "explanation_request": 600,
            "casual_conversation": 400,
            "creative_writing": 300,
            
            # General Knowledge
            "historical_facts": 400,
            "science_facts": 450,
            "geography_facts": 300,
            
            # Pop Culture
            "movies_tv": 350,
            "music_culture": 300,
            "sports_recreation": 350,
            "food_culture": 300,
            "technology_trends": 250,
            "lifestyle_wellness": 200,
            
            # Device & Technology Expertise (New)
            "device_technology": 60,
            "wifi_networking": 50,
            "security_privacy": 80,
            "diagnostic_troubleshooting": 55,
            "educational_tutoring": 90,
            "trivia_facts": 70
        }
        
        estimates = {}
        for category, count in category_counts.items():
            estimates[category] = {
                "recommended_count": count,
                "estimated_cost": f"${(count * base_cost_per_doc):.2f}",
                "estimated_time": f"{int((count * base_time_per_doc) / 60)} minutes",
                "description": self.get_category_descriptions()[category]
            }
        
        # Calculate totals
        total_count = sum(category_counts.values())
        total_cost = total_count * base_cost_per_doc
        total_time = int((total_count * base_time_per_doc) / 60)
        
        estimates["TOTAL"] = {
            "recommended_count": total_count,
            "estimated_cost": f"${total_cost:.2f}",
            "estimated_time": f"{total_time} minutes ({total_time // 60}h {total_time % 60}m)",
            "description": "Complete comprehensive knowledge base"
        }
        
        return estimates

def test_expanded_generation():
    """Test expanded synthetic document generation"""
    print("🧪 Testing Expanded M1K3 Knowledge Generation")
    print("=" * 70)
    
    generator = ExpandedSyntheticGenerator("test_expanded_kb.json")
    
    # Show category information
    print("\n📚 Available Categories:")
    descriptions = generator.get_category_descriptions()
    for category, description in descriptions.items():
        template_count = len(generator.templates.get(category, []))
        print(f"  • {category.replace('_', ' ').title()}: {description} ({template_count} templates)")
    
    # Show generation estimates
    print(f"\n💰 Generation Estimates:")
    estimates = generator.get_generation_estimates()
    
    # Show a few key categories
    key_categories = ["historical_facts", "movies_tv", "science_facts", "food_culture", "TOTAL"]
    for category in key_categories:
        if category in estimates:
            est = estimates[category]
            if category == "TOTAL":
                print(f"\n  🎯 {category}:")
            else:
                print(f"  📋 {category.replace('_', ' ').title()}:")
            print(f"     Documents: {est['recommended_count']}")
            print(f"     Cost: {est['estimated_cost']}")
            print(f"     Time: {est['estimated_time']}")
    
    # Test generation from new categories
    print(f"\n🚀 Testing New Category Generation:")
    
    test_categories = [
        ("historical_facts", 3),
        ("science_facts", 3), 
        ("movies_tv", 2),
        ("food_culture", 2)
    ]
    
    def progress_callback(current, total, title):
        print(f"    📝 {current}/{total}: {title[:50]}...")
    
    total_generated = 0
    for category, count in test_categories:
        print(f"\n  🎭 Generating {count} {category} documents...")
        
        result = generator.generate_and_save(
            category=category,
            count=count,
            progress_callback=progress_callback
        )
        
        if result['success']:
            print(f"  ✅ {category}: {result['count']} documents in {result['time_taken']:.2f}s")
            total_generated += result['count']
        else:
            print(f"  ❌ {category}: {result['message']}")
    
    print(f"\n📊 Test Results:")
    print(f"  • Total categories available: {len(descriptions)}")
    print(f"  • Total templates: {sum(len(templates) for templates in generator.templates.values())}")
    print(f"  • Documents generated in test: {total_generated}")
    print(f"  • Knowledge base: test_expanded_kb.json")
    
    # Clean up
    import os
    if os.path.exists("test_expanded_kb.json"):
        os.remove("test_expanded_kb.json")
        print(f"  🗑️  Cleaned up test files")
    
    print(f"\n✅ Expanded knowledge generation test completed!")

if __name__ == "__main__":
    test_expanded_generation()