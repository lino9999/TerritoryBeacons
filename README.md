TerritoryBeacons Plugin - Feature Overview
Brief Description
TerritoryBeacons is a Minecraft server plugin that allows players to create protected territories using activated beacon blocks. The plugin provides land claiming, protection, and management systems with visual boundaries and automatic decay mechanisms.
Core Features
Territory Creation & Management

Beacon-Based Claims: Players create territories by placing and activating beacon blocks with pyramid structures
Configurable Radius: Territory size scales with beacon tier level (configurable base radius + tier multiplier)
Overlap Prevention: System prevents territories from overlapping with existing claims
Visual Boundaries: Automatic placement of torch markers around territory perimeters

Protection System

Build Protection: Prevents unauthorized block placement and destruction within claimed territories
Explosion Protection: Blocks TNT and other explosions from damaging territory blocks
Border Protection: Territory boundary markers cannot be broken by non-owners
Owner Privileges: Territory owners have full building rights within their claims

Territory Information & Interaction

Interactive Beacons: Right-clicking territory beacons displays detailed information (owner, radius, level, influence)
Entry/Exit Notifications: Players receive title messages and sound alerts when entering or leaving territories
Territory Status: Real-time tracking of player locations within territories

Influence & Decay System

Territory Influence: Each territory has an influence value that decreases when owners are offline
Automatic Decay: Territories gradually lose influence and eventually disappear if owners remain inactive
Configurable Decay Time: Server administrators can set custom offline time limits (default: 72 hours)
Influence Recovery: Territory influence slowly regenerates when owners are online

Administrative Features

Persistent Storage: Territory data is automatically saved to YAML configuration files
Auto-Save System: Regular automatic saving every 5 minutes to prevent data loss
Configuration Options: Customizable base radius, tier multipliers, and decay timing
Command System: Built-in territory management commands (references TerritoryCommand class)

Visual & Audio Effects

Creation Effects: Particle effects when territories are successfully established
Boundary Visualization: Torch placement at territory borders for clear visual demarcation
Sound Notifications: Audio cues for territory entry/exit events
Title Messages: Clear on-screen notifications for territory status changes

Technical Specifications

Multi-World Support: Works across multiple Minecraft worlds
Concurrent Processing: Thread-safe territory management using ConcurrentHashMap
Performance Optimized: Efficient player position checking and territory validation
Event-Driven: Responsive to block placement, destruction, and player interactions
Configurable Parameters: Extensive customization options through plugin configuration files
