/**
 * 間 AI GitHub Model Explorer
 *
 * Auto-discover .glb files from GitHub repositories.
 * Search, browse, and load models from the world's largest code host!
 *
 * @murphysig v1.0.0 - GitHub integration for unlimited models
 * @confidence 0.85 - GitHub API is well-documented, but rate limits apply
 * @context Turns GitHub into a massive 3D model database
 */

/**
 * GitHub search result for a .glb file
 */
export interface GitHubModelResult {
  /** File name */
  name: string;
  /** Full path in repo */
  path: string;
  /** Repository owner/name */
  repo: string;
  /** Direct download URL (raw.githubusercontent.com) */
  downloadUrl: string;
  /** File size in bytes */
  size: number;
  /** Repository stars */
  stars?: number;
  /** Repository description */
  repoDescription?: string;
}

/**
 * GitHub repository info
 */
export interface GitHubRepo {
  owner: string;
  name: string;
  fullName: string;
  description: string;
  stars: number;
  url: string;
  topics: string[];
}

/**
 * Search options
 */
export interface GitHubSearchOptions {
  /** Search query */
  query?: string;
  /** Max file size in bytes (default: 10MB) */
  maxSize?: number;
  /** Include only repos with these topics */
  topics?: string[];
  /** Minimum stars (default: 0) */
  minStars?: number;
  /** Max results (default: 30) */
  limit?: number;
  /** GitHub token for higher rate limits (optional) */
  token?: string;
}

/**
 * GitHub Model Explorer
 *
 * Discovers .glb files from GitHub using the public API.
 */
export class GitHubModelExplorer {
  private baseUrl = "https://api.github.com";
  private token?: string;

  constructor(token?: string) {
    this.token = token;
  }

  /**
   * Search GitHub for .glb files
   *
   * Examples:
   * - searchModels({ query: "pokemon" })
   * - searchModels({ topics: ["gltf", "3d-models"], minStars: 10 })
   */
  async searchModels(options: GitHubSearchOptions = {}): Promise<GitHubModelResult[]> {
    const {
      query = "",
      maxSize = 10 * 1024 * 1024, // 10MB
      minStars = 0,
      limit = 30,
    } = options;

    // Build search query
    let searchQuery = `extension:glb`;

    if (query) {
      searchQuery += ` ${query}`;
    }

    if (maxSize) {
      searchQuery += ` size:<${maxSize}`;
    }

    const url = `${this.baseUrl}/search/code?q=${encodeURIComponent(searchQuery)}&per_page=${limit}`;

    const response = await this.fetch(url);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(`GitHub API error: ${data.message || response.statusText}`);
    }

    // Transform results
    const results: GitHubModelResult[] = [];

    for (const item of data.items || []) {
      // Get repo info to check stars
      if (minStars > 0) {
        try {
          const repoInfo = await this.getRepoInfo(item.repository.full_name);
          if (repoInfo.stars < minStars) {
            continue;
          }
        } catch {
          continue;
        }
      }

      results.push({
        name: item.name,
        path: item.path,
        repo: item.repository.full_name,
        downloadUrl: this.getDownloadUrl(item.repository.full_name, item.path),
        size: 0, // Not available in search API
        repoDescription: item.repository.description,
      });
    }

    return results;
  }

  /**
   * Find all .glb files in a specific repository
   *
   * Example:
   * - findInRepo("KhronosGroup/glTF-Sample-Models")
   */
  async findInRepo(repoFullName: string): Promise<GitHubModelResult[]> {
    const [owner, repo] = repoFullName.split("/");
    if (!owner || !repo) {
      throw new Error("Invalid repo format. Use: owner/repo");
    }

    // Get default branch
    const repoInfo = await this.getRepoInfo(repoFullName);
    const defaultBranch = repoInfo.url.split("/").pop() || "main";

    // Get file tree (recursive)
    const url = `${this.baseUrl}/repos/${repoFullName}/git/trees/${defaultBranch}?recursive=1`;
    const response = await this.fetch(url);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(`GitHub API error: ${data.message || response.statusText}`);
    }

    // Filter for .glb files
    const glbFiles = data.tree.filter(
      (item: any) =>
        item.type === "blob" &&
        (item.path.endsWith(".glb") || item.path.endsWith(".gltf"))
    );

    return glbFiles.map((file: any) => ({
      name: file.path.split("/").pop(),
      path: file.path,
      repo: repoFullName,
      downloadUrl: this.getDownloadUrl(repoFullName, file.path, defaultBranch),
      size: file.size,
      stars: repoInfo.stars,
      repoDescription: repoInfo.description,
    }));
  }

  /**
   * Search for repositories with 3D model content
   */
  async findModelRepositories(options: GitHubSearchOptions = {}): Promise<GitHubRepo[]> {
    const {
      query = "",
      topics = ["gltf", "3d-models", "glb"],
      minStars = 10,
      limit = 30,
    } = options;

    // Build query
    let searchQuery = topics.map((t) => `topic:${t}`).join("+");
    if (query) {
      searchQuery += `+${query}`;
    }
    if (minStars) {
      searchQuery += `+stars:>=${minStars}`;
    }

    const url = `${this.baseUrl}/search/repositories?q=${encodeURIComponent(searchQuery)}&sort=stars&per_page=${limit}`;

    const response = await this.fetch(url);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(`GitHub API error: ${data.message || response.statusText}`);
    }

    return (data.items || []).map((repo: any) => ({
      owner: repo.owner.login,
      name: repo.name,
      fullName: repo.full_name,
      description: repo.description || "",
      stars: repo.stargazers_count,
      url: repo.html_url,
      topics: repo.topics || [],
    }));
  }

  /**
   * Get curated list of popular model repositories
   */
  async getCuratedRepos(): Promise<GitHubRepo[]> {
    const knownRepos = [
      "KhronosGroup/glTF-Sample-Models",
      "mrdoob/three.js",
      "Quaternius/Ultimate-Low-Poly-Blocks",
      "google/model-viewer",
    ];

    const repos: GitHubRepo[] = [];
    for (const repoName of knownRepos) {
      try {
        const info = await this.getRepoInfo(repoName);
        repos.push(info);
      } catch (error) {
        console.warn(`Failed to fetch ${repoName}:`, error);
      }
    }

    return repos;
  }

  /**
   * Get rate limit status
   */
  async getRateLimit(): Promise<{ remaining: number; limit: number; resetAt: Date }> {
    const url = `${this.baseUrl}/rate_limit`;
    const response = await this.fetch(url);
    const data = await response.json();

    const core = data.resources.core;
    return {
      remaining: core.remaining,
      limit: core.limit,
      resetAt: new Date(core.reset * 1000),
    };
  }

  // ========================================================================
  // Private Helpers
  // ========================================================================

  private async getRepoInfo(fullName: string): Promise<GitHubRepo> {
    const url = `${this.baseUrl}/repos/${fullName}`;
    const response = await this.fetch(url);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(`Failed to fetch repo: ${data.message}`);
    }

    return {
      owner: data.owner.login,
      name: data.name,
      fullName: data.full_name,
      description: data.description || "",
      stars: data.stargazers_count,
      url: data.html_url,
      topics: data.topics || [],
    };
  }

  private getDownloadUrl(repoFullName: string, filePath: string, branch = "master"): string {
    return `https://raw.githubusercontent.com/${repoFullName}/${branch}/${filePath}`;
  }

  private async fetch(url: string): Promise<Response> {
    const headers: HeadersInit = {
      Accept: "application/vnd.github+json",
    };

    if (this.token) {
      headers.Authorization = `Bearer ${this.token}`;
    }

    return fetch(url, { headers });
  }
}

/**
 * Singleton instance (no token)
 */
export const githubExplorer = new GitHubModelExplorer();

/**
 * Format file size for display
 */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Example usage:
 *
 * // Search for Pokemon models
 * const results = await githubExplorer.searchModels({ query: "pokemon" });
 *
 * // Find all models in Khronos repo
 * const models = await githubExplorer.findInRepo("KhronosGroup/glTF-Sample-Models");
 *
 * // Discover model repositories
 * const repos = await githubExplorer.findModelRepositories({ minStars: 50 });
 *
 * // Check rate limit
 * const limit = await githubExplorer.getRateLimit();
 * console.log(`${limit.remaining}/${limit.limit} requests remaining`);
 */
