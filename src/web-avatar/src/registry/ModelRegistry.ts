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
