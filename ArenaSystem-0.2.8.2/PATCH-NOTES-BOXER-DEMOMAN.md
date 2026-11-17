# Class Selector UI Follow-up

This patch is limited to the TDM class-selection interface.

- The class selector now uses two pages with four class entries per page.
- Page 1: Knight, Tank, Archer, Sniper.
- Page 2: Assassin, Ninja, Boxer, Demoman.
- Class entries are placed in slots 10, 12, 14, and 16, with one separator slot between them.
- Previous, page indicator, and Next are in slots 18, 22, and 26.
- Class selection is resolved from the active page's slot mapping.
- Click and drag actions are cancelled while the selector is open.

No class ability effects, damage values, cooldowns, projectiles, or mine behavior are changed by this UI-only patch.
