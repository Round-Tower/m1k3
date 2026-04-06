/**
 * 間 AI Model Registry (TypeScript Port)
 *
 * Central registry for 3D avatar models.
 * Manages multiple model configurations and provides
 * a unified API for model selection and loading.
 *
 * Ported from: app/composeApp/src/commonMain/.../avatar/ModelRegistry.kt
 */

/**
 * Model type - static (no skeleton) or animated (rigged)
 */
export type ModelType = "STATIC" | "ANIMATED";

/**
 * Model configuration
 *
 * Describes a 3D model and its properties.
 */
export interface ModelConfig {
  /** Unique identifier (e.g., "mask", "colobus", "sparrow") */
  id: string;
  /** Display name (e.g., "Mask", "Colobus Monkey") */
  name: string;
  /** Asset path (e.g., "models/Mask.glb") */
  path: string;
  /** Optional description */
  description: string;
  /** Optional thumbnail path */
  thumbnail?: string;
  /** Model category (e.g., "static", "mammal", "bird", "fish") */
  category: string;
  /** Whether model is STATIC or ANIMATED */
  modelType: ModelType;
  /** Whether model has baked skeleton animations */
  hasAnimations: boolean;
  /** Whether model can display emotions */
  supportsEmotions: boolean;
  /** Whether this is recommended starting model */
  defaultForNewUsers: boolean;
  /** Optional license attribution */
  attribution?: string;
}

/**
 * Pre-configured model configs
 */
export const MODELS = {
  /**
   * Simple mask (alternative avatar)
   * Static model with procedural animations (no skeleton).
   * License: CC-BY-4.0 by IzLoM39 (Sketchfab)
   */
  MASK: {
    id: "mask",
    name: "Mask",
    path: "models/Mask.glb",
    description:
      "Simple mask with procedural animations (rotation, scale, color)",
    category: "static",
    modelType: "STATIC" as ModelType,
    hasAnimations: false,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC-BY-4.0 by IzLoM39 (Sketchfab)",
  },

  /**
   * Colobus monkey (animated) - DEFAULT
   * Eco-consciousness showcase with lifelike animations.
   */
  COLOBUS: {
    id: "colobus",
    name: "Colobus Monkey",
    path: "models/Colobus_Animations.glb",
    description: "Black and white colobus monkey with 18 animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: true,
  },

  /** Sparrow (bird) */
  SPARROW: {
    id: "sparrow",
    name: "Sparrow",
    path: "models/Sparrow_Animations.glb",
    description: "Small bird with flying and perching animations",
    category: "bird",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Gecko (reptile) */
  GECKO: {
    id: "gecko",
    name: "Gecko",
    path: "models/Gecko_Animations.glb",
    description: "Colorful gecko with climbing animations",
    category: "reptile",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Herring (fish) */
  HERRING: {
    id: "herring",
    name: "Herring",
    path: "models/Herring_Animations.glb",
    description: "Swimming fish with aquatic animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Muskrat (mammal) */
  MUSKRAT: {
    id: "muskrat",
    name: "Muskrat",
    path: "models/Muskrat_Animations.glb",
    description: "Semi-aquatic rodent with swimming animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Pudu (mammal) */
  PUDU: {
    id: "pudu",
    name: "Pudu",
    path: "models/Pudu_Animations.glb",
    description: "Smallest deer species with running animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Taipan (reptile) */
  TAIPAN: {
    id: "taipan",
    name: "Taipan",
    path: "models/Taipan_Animations.glb",
    description: "Venomous snake with slithering animations",
    category: "reptile",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  /** Inkfish/Squid (cephalopod) */
  INKFISH: {
    id: "inkfish",
    name: "Inkfish",
    path: "models/Inkfish_Animations.glb",
    description: "Squid with jet propulsion animations",
    category: "cephalopod",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
  },

  // ============================================================
  // Community Models (CC0 / Public Domain)
  // ============================================================

  /** Fox (Khronos glTF sample) */
  FOX: {
    id: "fox",
    name: "Fox",
    path: "models/Fox.glb",
    description: "Low-poly fox with Survey, Walk, Run animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - PixelMannen via Khronos glTF Samples",
  },

  /** Cesium Man (Khronos glTF sample) */
  CESIUM_MAN: {
    id: "cesium-man",
    name: "Cesium Man",
    path: "models/CesiumMan.glb",
    description: "Animated human figure with walking animation",
    category: "humanoid",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Cesium via Khronos glTF Samples",
  },

  /** BrainStem Robot (Khronos glTF sample) */
  BRAINSTEM: {
    id: "brainstem",
    name: "BrainStem Robot",
    path: "models/BrainStem.glb",
    description: "Animated robot with multiple joint animations",
    category: "robot",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Smith Micro via Khronos glTF Samples",
  },

  /** Simple Robot (sokobot-3d) */
  ROBOT: {
    id: "robot",
    name: "Simple Robot",
    path: "models/robot.glb",
    description: "Cute low-poly robot",
    category: "robot",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - sokobot-3d project",
  },

  // ============================================================
  // Quaternius Animated Animal Pack (CC0)
  // poly.pizza/bundle/Animated-Animal-Pack-ILAPXeUYiS
  // ============================================================

  COW: {
    id: "cow",
    name: "Cow",
    path: "models/Cow.glb",
    description: "Farmyard cow with walk, run, attack, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  DONKEY: {
    id: "donkey",
    name: "Donkey",
    path: "models/Donkey.glb",
    description: "Stubborn donkey with kick, gallop, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  DEER: {
    id: "deer",
    name: "Deer",
    path: "models/Deer.glb",
    description: "Graceful deer with walk, run, jump, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  ALPACA: {
    id: "alpaca",
    name: "Alpaca",
    path: "models/Alpaca.glb",
    description: "Fluffy alpaca with walk, spit, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  BULL: {
    id: "bull",
    name: "Bull",
    path: "models/Bull.glb",
    description: "Powerful bull with charge, stomp, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  QFOX: {
    id: "qfox",
    name: "Quaternius Fox",
    path: "models/QFox.glb",
    description: "Low-poly fox with walk, run, attack, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  SHIBA_INU: {
    id: "shiba-inu",
    name: "Shiba Inu",
    path: "models/ShibaInu.glb",
    description: "Iconic Shiba Inu dog with sit, bark, and run animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  STAG: {
    id: "stag",
    name: "Stag",
    path: "models/Stag.glb",
    description: "Antlered stag with walk, gallop, and roar animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  HUSKY: {
    id: "husky",
    name: "Husky",
    path: "models/Husky.glb",
    description: "Arctic Husky dog with run, howl, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  WOLF: {
    id: "wolf",
    name: "Wolf",
    path: "models/Wolf.glb",
    description: "Wild wolf with prowl, howl, and attack animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  WHITE_HORSE: {
    id: "white-horse",
    name: "White Horse",
    path: "models/WhiteHorse.glb",
    description: "White horse with gallop, rear, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  HORSE: {
    id: "horse",
    name: "Horse",
    path: "models/Horse.glb",
    description: "Brown horse with gallop, rear, and idle animations",
    category: "mammal",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  // ============================================================
  // Quaternius Animated Dinosaur Pack (CC0)
  // poly.pizza/bundle/Animated-Dinosaur-Bundle-SmoLdBLO2K
  // ============================================================

  TREX: {
    id: "trex",
    name: "T-Rex",
    path: "models/TRex.glb",
    description: "Tyrannosaurus Rex with roar, stomp, and attack animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  PARASAUROLOPHUS: {
    id: "parasaurolophus",
    name: "Parasaurolophus",
    path: "models/Parasaurolophus.glb",
    description: "Crested hadrosaur with walk, run, and idle animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  VELOCIRAPTOR: {
    id: "velociraptor",
    name: "Velociraptor",
    path: "models/Velociraptor.glb",
    description: "Fast feathered raptor with sprint, slash, and idle animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  TRICERATOPS: {
    id: "triceratops",
    name: "Triceratops",
    path: "models/Triceratops.glb",
    description: "Three-horned dinosaur with charge, stomp, and idle animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  STEGOSAURUS: {
    id: "stegosaurus",
    name: "Stegosaurus",
    path: "models/Stegosaurus.glb",
    description: "Plated stegosaur with walk, tail-swing, and idle animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  APATOSAURUS: {
    id: "apatosaurus",
    name: "Apatosaurus",
    path: "models/Apatosaurus.glb",
    description: "Long-necked sauropod with lumbering walk and idle animations",
    category: "dinosaur",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  // ============================================================
  // Quaternius Animated Fish Pack (CC0)
  // poly.pizza/bundle/Animated-Fish-Bundle-44zhHN1UbT
  // ============================================================

  ANGLERFISH: {
    id: "anglerfish",
    name: "Anglerfish",
    path: "models/Anglerfish.glb",
    description: "Deep-sea anglerfish with swim and lure-glow animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  SHARK: {
    id: "shark",
    name: "Shark",
    path: "models/Shark.glb",
    description: "Ocean shark with patrol, dash, and idle swim animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  BLOBFISH: {
    id: "blobfish",
    name: "Blobfish",
    path: "models/Blobfish.glb",
    description: "Sad-faced deep-sea blobfish with float and idle animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  PUFFERFISH: {
    id: "pufferfish",
    name: "Pufferfish",
    path: "models/Pufferfish.glb",
    description: "Spiky pufferfish with inflate, deflate, and swim animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  PIRANHA: {
    id: "piranha",
    name: "Piranha",
    path: "models/Piranha.glb",
    description: "Ferocious piranha with bite, frenzy, and patrol animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  KOI: {
    id: "koi",
    name: "Koi",
    path: "models/Koi.glb",
    description: "Elegant Japanese koi with graceful swim and surface animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  CLOWNFISH: {
    id: "clownfish",
    name: "Clownfish",
    path: "models/Clownfish.glb",
    description: "Bright orange clownfish with swim and dart animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  SWORDFISH: {
    id: "swordfish",
    name: "Swordfish",
    path: "models/Swordfish.glb",
    description: "Speedy swordfish with sprint and leap animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  BETTA: {
    id: "betta",
    name: "Betta",
    path: "models/Betta.glb",
    description: "Vivid betta fish with fin-flare and swim animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },

  GOBLIN_SHARK: {
    id: "goblin-shark",
    name: "Goblin Shark",
    path: "models/GoblinShark.glb",
    description: "Prehistoric goblin shark with jaw-extend and swim animations",
    category: "fish",
    modelType: "ANIMATED" as ModelType,
    hasAnimations: true,
    supportsEmotions: true,
    defaultForNewUsers: false,
    attribution: "CC0 - Quaternius via Poly Pizza",
  },
} as const satisfies Record<string, ModelConfig>;

/**
 * Default model ID
 */
export const DEFAULT_MODEL_ID = "colobus";

/**
 * Model Registry
 *
 * Central registry for all available 3D models.
 */
class ModelRegistryImpl {
  private predefinedModels: ModelConfig[] = Object.values(MODELS);
  private customModels: ModelConfig[] = [];

  /** All available models (predefined + custom) */
  get allModels(): ModelConfig[] {
    return [...this.predefinedModels, ...this.customModels];
  }

  /** Get model by ID */
  getById(id: string): ModelConfig | undefined {
    return this.allModels.find((m) => m.id === id);
  }

  /** Get default model (Colobus Monkey) */
  getDefault(): ModelConfig {
    return MODELS.COLOBUS;
  }

  /** Get models by category */
  getByCategory(category: string): ModelConfig[] {
    return this.allModels.filter(
      (m) => m.category.toLowerCase() === category.toLowerCase()
    );
  }

  /** Register custom model at runtime */
  register(config: ModelConfig): boolean {
    if (this.allModels.some((m) => m.id === config.id)) {
      return false; // ID collision
    }
    this.customModels.push(config);
    return true;
  }

  /** Unregister custom model */
  unregister(id: string): boolean {
    const index = this.customModels.findIndex((m) => m.id === id);
    if (index >= 0) {
      this.customModels.splice(index, 1);
      return true;
    }
    return false;
  }

  /** Check if model ID exists */
  exists(id: string): boolean {
    return this.allModels.some((m) => m.id === id);
  }

  /** Get all categories */
  getCategories(): string[] {
    return [...new Set(this.allModels.map((m) => m.category))].sort();
  }

  /** Search models by name or description */
  search(query: string): ModelConfig[] {
    const lowerQuery = query.toLowerCase();
    return this.allModels.filter(
      (m) =>
        m.name.toLowerCase().includes(lowerQuery) ||
        m.description.toLowerCase().includes(lowerQuery) ||
        m.id.toLowerCase().includes(lowerQuery)
    );
  }

  /** Get model count */
  get count(): number {
    return this.allModels.length;
  }

  /** Get predefined model count */
  get predefinedCount(): number {
    return this.predefinedModels.length;
  }

  /** Get custom model count */
  get customCount(): number {
    return this.customModels.length;
  }

  /** Check if model is the default */
  isDefault(model: ModelConfig): boolean {
    return model.id === DEFAULT_MODEL_ID;
  }

  /** Get file name from model path */
  getFileName(model: ModelConfig): string {
    return model.path.split("/").pop() ?? model.path;
  }
}

/** Singleton model registry instance */
export const ModelRegistry = new ModelRegistryImpl();
