/**
 * M1K3 Navigation Component
 * Responsive navigation using the pure black design system
 */

class M1K3Navigation {
    constructor() {
        this.menuOpen = false;
        this.currentRoute = window.location.hash.slice(1) || '/';
        this.developmentMode = this.detectDevelopmentMode();
    }

    /**
     * Detect if running in development mode
     */
    detectDevelopmentMode() {
        return window.location.hostname === 'localhost' || 
               window.location.hostname === '127.0.0.1' ||
               window.location.search.includes('dev=true');
    }

    /**
     * Create navigation HTML
     */
    render() {
        const navItems = this.getNavigationItems();
        
        return `
            <nav class="m1k3-nav" role="navigation" aria-label="Main navigation">
                <!-- Mobile Menu Toggle -->
                <button class="nav-toggle" aria-label="Toggle navigation" aria-expanded="false">
                    <span class="nav-toggle-line"></span>
                    <span class="nav-toggle-line"></span>
                    <span class="nav-toggle-line"></span>
                </button>

                <!-- Logo/Brand -->
                <div class="nav-brand">
                    <a href="#/" class="nav-brand-link">
                        <div class="nav-logo">
                            <span class="nav-logo-text">M1K3</span>
                            <span class="nav-logo-badge">AI</span>
                        </div>
                    </a>
                </div>

                <!-- Navigation Links -->
                <div class="nav-menu" aria-hidden="true">
                    <ul class="nav-list">
                        ${navItems.map(item => this.renderNavItem(item)).join('')}
                    </ul>
                </div>

                <!-- Status Indicator -->
                <div class="nav-status">
                    <span class="nav-status-dot"></span>
                    <span class="nav-status-text">Local</span>
                </div>
            </nav>
        `;
    }

    /**
     * Get navigation items based on environment
     */
    getNavigationItems() {
        const items = [
            {
                path: '/',
                label: 'Home',
                icon: '🏠',
                always: true
            },
            {
                path: '/dashboard',
                label: 'Dashboard',
                icon: '🎭',
                always: true
            },
            {
                path: '/chat',
                label: 'Chat',
                icon: '💬',
                always: true
            },
            {
                path: '/settings',
                label: 'Settings',
                icon: '⚙️',
                always: true
            },
            {
                path: '/docs',
                label: 'Docs',
                icon: '📚',
                always: true
            }
        ];

        // Add development items if in dev mode
        if (this.developmentMode) {
            items.push(
                {
                    path: '/debug',
                    label: 'Debug',
                    icon: '🐛',
                    devOnly: true
                },
                {
                    path: '/test',
                    label: 'Tests',
                    icon: '🧪',
                    devOnly: true
                }
            );
        }

        return items;
    }

    /**
     * Render individual navigation item
     */
    renderNavItem(item) {
        const isActive = this.currentRoute === item.path;
        const activeClass = isActive ? 'nav-link-active' : '';
        const devClass = item.devOnly ? 'nav-link-dev' : '';
        
        return `
            <li class="nav-item ${devClass}">
                <a href="#${item.path}" 
                   class="nav-link ${activeClass}" 
                   data-route="${item.path}"
                   aria-current="${isActive ? 'page' : 'false'}">
                    <span class="nav-link-icon">${item.icon}</span>
                    <span class="nav-link-text">${item.label}</span>
                </a>
            </li>
        `;
    }

    /**
     * Create navigation styles using design tokens
     */
    getStyles() {
        return `
            /* Navigation Container */
            .m1k3-nav {
                position: fixed;
                top: 0;
                left: 0;
                right: 0;
                z-index: 1000;
                background: var(--bg-primary);
                border-bottom: 1px solid var(--border-subtle);
                backdrop-filter: blur(10px);
                -webkit-backdrop-filter: blur(10px);
                height: 60px;
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 0 var(--space-lg);
                transition: all var(--duration-normal) var(--easing-smooth);
            }

            /* Mobile Menu Toggle */
            .nav-toggle {
                display: none;
                flex-direction: column;
                justify-content: space-between;
                width: 24px;
                height: 18px;
                background: transparent;
                border: none;
                cursor: pointer;
                padding: 0;
                z-index: 1002;
            }

            .nav-toggle-line {
                display: block;
                width: 100%;
                height: 2px;
                background: var(--text-primary);
                transition: all var(--duration-fast) var(--easing-smooth);
                border-radius: var(--radius-sm);
            }

            .nav-toggle[aria-expanded="true"] .nav-toggle-line:nth-child(1) {
                transform: translateY(8px) rotate(45deg);
            }

            .nav-toggle[aria-expanded="true"] .nav-toggle-line:nth-child(2) {
                opacity: 0;
            }

            .nav-toggle[aria-expanded="true"] .nav-toggle-line:nth-child(3) {
                transform: translateY(-8px) rotate(-45deg);
            }

            /* Brand/Logo */
            .nav-brand {
                display: flex;
                align-items: center;
            }

            .nav-brand-link {
                text-decoration: none;
                color: var(--text-primary);
            }

            .nav-logo {
                display: flex;
                align-items: center;
                gap: var(--space-xs);
            }

            .nav-logo-text {
                font-size: 1.5rem;
                font-weight: 700;
                letter-spacing: -0.02em;
                background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-secondary) 100%);
                -webkit-background-clip: text;
                -webkit-text-fill-color: transparent;
                background-clip: text;
            }

            .nav-logo-badge {
                font-size: 0.625rem;
                font-weight: 600;
                padding: 2px 6px;
                background: var(--bg-elevated);
                border: 1px solid var(--border-light);
                border-radius: var(--radius-sm);
                text-transform: uppercase;
                letter-spacing: 0.05em;
            }

            /* Navigation Menu */
            .nav-menu {
                flex: 1;
                display: flex;
                justify-content: center;
            }

            .nav-list {
                display: flex;
                list-style: none;
                margin: 0;
                padding: 0;
                gap: var(--space-xs);
            }

            .nav-item {
                margin: 0;
            }

            /* Navigation Links */
            .nav-link {
                display: flex;
                align-items: center;
                gap: var(--space-xs);
                padding: var(--space-sm) var(--space-md);
                color: var(--text-secondary);
                text-decoration: none;
                border-radius: var(--radius-md);
                transition: all var(--duration-fast) var(--easing-smooth);
                font-size: 0.875rem;
                font-weight: 500;
                position: relative;
                white-space: nowrap;
            }

            .nav-link:hover {
                color: var(--text-primary);
                background: var(--bg-secondary);
            }

            .nav-link-active {
                color: var(--text-primary);
                background: var(--bg-tertiary);
            }

            .nav-link-active::after {
                content: '';
                position: absolute;
                bottom: -1px;
                left: var(--space-md);
                right: var(--space-md);
                height: 2px;
                background: var(--accent-primary);
                border-radius: var(--radius-sm);
            }

            .nav-link-icon {
                font-size: 1.125rem;
                line-height: 1;
            }

            .nav-link-text {
                font-size: 0.875rem;
            }

            /* Development Links */
            .nav-link-dev .nav-link {
                color: var(--text-muted);
            }

            .nav-link-dev .nav-link::before {
                content: '';
                position: absolute;
                top: 4px;
                right: 4px;
                width: 4px;
                height: 4px;
                background: var(--accent-m1k3);
                border-radius: 50%;
            }

            /* Status Indicator */
            .nav-status {
                display: flex;
                align-items: center;
                gap: var(--space-xs);
                padding: var(--space-xs) var(--space-sm);
                background: var(--bg-secondary);
                border-radius: var(--radius-lg);
                border: 1px solid var(--border-subtle);
            }

            .nav-status-dot {
                width: 8px;
                height: 8px;
                background: #10b981;
                border-radius: 50%;
                animation: pulse 2s infinite;
            }

            .nav-status-text {
                font-size: 0.75rem;
                color: var(--text-secondary);
                font-weight: 500;
            }

            @keyframes pulse {
                0%, 100% { opacity: 1; }
                50% { opacity: 0.5; }
            }

            /* Mobile Responsive */
            @media (max-width: 768px) {
                .nav-toggle {
                    display: flex;
                }

                .nav-menu {
                    position: fixed;
                    top: 60px;
                    left: 0;
                    right: 0;
                    bottom: 0;
                    background: var(--bg-primary);
                    padding: var(--space-lg);
                    transform: translateX(-100%);
                    transition: transform var(--duration-normal) var(--easing-smooth);
                }

                .nav-menu[aria-hidden="false"] {
                    transform: translateX(0);
                }

                .nav-list {
                    flex-direction: column;
                    gap: var(--space-sm);
                }

                .nav-link {
                    padding: var(--space-md);
                    font-size: 1rem;
                }

                .nav-link-icon {
                    font-size: 1.25rem;
                }

                .nav-status {
                    display: none;
                }
            }

            /* Tablet Adjustments */
            @media (max-width: 1024px) {
                .nav-link-text {
                    display: none;
                }

                .nav-link {
                    padding: var(--space-sm);
                }

                .nav-link-icon {
                    font-size: 1.25rem;
                }
            }
        `;
    }

    /**
     * Initialize navigation
     */
    init() {
        // Inject navigation HTML
        const navContainer = document.getElementById('navigation') || 
                           document.querySelector('header') ||
                           document.body;
        
        if (navContainer === document.body) {
            const nav = document.createElement('div');
            nav.id = 'navigation';
            document.body.insertBefore(nav, document.body.firstChild);
            nav.innerHTML = this.render();
        } else {
            navContainer.innerHTML = this.render();
        }

        // Inject styles
        const styleSheet = document.createElement('style');
        styleSheet.textContent = this.getStyles();
        document.head.appendChild(styleSheet);

        // Add body padding to account for fixed nav
        document.body.style.paddingTop = '60px';

        // Initialize event listeners
        this.attachEventListeners();

        // Update active state on route change
        window.addEventListener('hashchange', () => {
            this.updateActiveState();
        });
    }

    /**
     * Attach event listeners
     */
    attachEventListeners() {
        // Mobile menu toggle
        const toggle = document.querySelector('.nav-toggle');
        const menu = document.querySelector('.nav-menu');
        
        if (toggle && menu) {
            toggle.addEventListener('click', () => {
                this.menuOpen = !this.menuOpen;
                toggle.setAttribute('aria-expanded', this.menuOpen);
                menu.setAttribute('aria-hidden', !this.menuOpen);
            });
        }

        // Close mobile menu on link click
        document.querySelectorAll('.nav-link').forEach(link => {
            link.addEventListener('click', () => {
                if (this.menuOpen) {
                    this.menuOpen = false;
                    toggle?.setAttribute('aria-expanded', false);
                    menu?.setAttribute('aria-hidden', true);
                }
            });
        });

        // Close mobile menu on outside click
        document.addEventListener('click', (e) => {
            if (this.menuOpen && !e.target.closest('.m1k3-nav')) {
                this.menuOpen = false;
                toggle?.setAttribute('aria-expanded', false);
                menu?.setAttribute('aria-hidden', true);
            }
        });
    }

    /**
     * Update active navigation state
     */
    updateActiveState() {
        this.currentRoute = window.location.hash.slice(1) || '/';
        
        document.querySelectorAll('.nav-link').forEach(link => {
            const route = link.getAttribute('data-route');
            const isActive = route === this.currentRoute;
            
            if (isActive) {
                link.classList.add('nav-link-active');
                link.setAttribute('aria-current', 'page');
            } else {
                link.classList.remove('nav-link-active');
                link.setAttribute('aria-current', 'false');
            }
        });
    }
}

// Create and export navigation instance
const navigation = new M1K3Navigation();

// Auto-initialize when DOM is ready
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', () => navigation.init());
} else {
    navigation.init();
}

// Export for ES6 modules
export default navigation;

// Also attach to window for global access
window.M1K3Navigation = M1K3Navigation;
window.navigation = navigation;