# Pushdozer - Professional Terrain Editing Tool

![pushdozer](https://cdn.modrinth.com/data/cached_images/8cebc3138a6e1ce2c904a0b8482809cb94a1e646_0.webp)

## ✨ Overview

Pushdozer is a powerful terrain editing mod designed specifically for Minecraft builders and creators. It makes terrain editing as simple and efficient as operating a bulldozer, providing intuitive multi-mode terrain operations, rich brush geometries, flexible elevation systems, and server-authoritative multiplayer synchronization with undo/redo functionality. Whether you're leveling mountains, digging tunnels, batch planting, or creating natural shorelines, Pushdozer makes all these tasks effortless.

## 🎯 Core Features

### 🛠️ Eight Working Modes
- **Excavate Mode** - Quickly clear large areas with support for layered excavation and block filtering
- **Place Mode** - Intelligently place surface blocks with adaptive biome support
- **Smooth Mode** - Intelligent terrain smoothing for handling steep edges and rough surfaces
- **Surface Roughen** - Add natural textures and roughness effects to terrain
- **Surface Convert** - Convert surface blocks to other types with multi-block mixing support
- **Bone Meal** - Use bone meal to promote plant growth
- **Batch Plant** - Intelligently plant trees, flowers, and grass with biome-adaptive support
- **Shoreline Process** - Create natural shorelines with support for beaches, embankments, and more

### 🎨 Eight Brush Geometries
- **Sphere** - Circular area editing, perfect for natural terrain
- **Box** - Square area editing, ideal for building foundations
- **Octahedron** - Diamond-shaped editing for unique visual effects
- **Cylinder** - Cylindrical area editing, great for tunnel digging
- **Cone** - Conical area editing, perfect for mountain shaping
- **Ellipsoid** - Elliptical area editing, suitable for natural terrain
- **Tetrahedron** - Triangular pyramid editing for artistic creation
- **Triangular Prism** - Triangular prism editing for special architectural needs

### 📐 Four Elevation Systems
- **No Limit** - Edit at any height freely, suitable for complex terrain
- **Follow Player** - Operation elevation follows player position
- **Locked Once** - Precise height control, perfect for leveling terrain
- **Custom** - Manually set specific heights

### 🖥️ Four Display Modes
- **Wireframe** - Display brush outlines with best performance
- **Point Cloud** - Show point cloud preview for intuitive visualization
- **Surface** - Semi-transparent face preview for coverage visualization
- **Hidden** - No preview display, focus on operations

### 🔄 Undo/Redo System
- Support for 30-step operation history
- Real-time operation record saving
- Cooldown mechanism to prevent frequent undo/redo

## 🎮 User-Friendly Design

### Intuitive Interface
- Modern configuration interface with simple and intuitive operations
- Real-time preview functionality, what you see is what you get
- Clearly categorized block and plant selection systems

### Smart Configuration
- Hotkey system customizable in Minecraft's key binding settings
- Automatic configuration file saving with persistent settings
- Support for multiple preset configuration schemes

### Performance Optimization
- Server-authoritative execution ensuring multiplayer consistency
- Batch network broadcasting optimizing large-scale operations
- Intelligent lighting updates with automatic neighbor block handling

## 🌍 Multiplayer Support

### Server Authority
- All operations executed on the server ensuring data consistency
- Client only responsible for interface interaction and preview display
- Prevention of cheating and abnormal operations

### Network Optimization
- Small operations sent immediately, large operations sent in batches
- Automatic handling of lighting and neighbor block updates
- Large-scale operations synchronized in chunks to avoid lag

### Permission Control
- Available to all players by default
- Built-in radius limits (default maximum 100)
- Integrable with region protection and permission systems

## ⚙️ Installation Requirements

### System Requirements
- **Minecraft**: 1.21.4
- **Fabric Loader**: >= 0.16.9
- **Fabric API**: Required
- **Java**: >= 17

### Installation Steps
1. Install [Fabric Mod Loader](https://fabricmc.net/use/) (version >= 0.16.9)
2. Download and install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest version of Pushdozer from the [releases page](https://modrinth.com/mod/pushdozer/versions)
4. Place the downloaded JAR file in your `.minecraft/mods` folder
5. Launch Minecraft and enjoy Pushdozer!

## 🎯 Use Cases

### Building Projects
- Level building foundations
- Dig basements and tunnels
- Create artificial lakes and water features

### Terrain Beautification
- Smooth mountains and hills
- Create natural shorelines
- Batch plant forests and gardens

### Survival Game
- Quickly clear areas
- Create farms and plantations
- Dig mines and channels

### Creative Mode
- Large-scale terrain transformation
- Artistic terrain creation
- Complex building projects

## 🔧 Technical Features

### High-Performance Architecture
- Optimized algorithm implementation supporting large-scale operations
- Intelligent memory management reducing resource usage
- Multi-threaded processing improving response speed

### Compatibility
- Compatible with most mods
- Support for custom blocks and plants
- Extensible API interface

### Stability
- Comprehensive error handling mechanisms
- Automatic backup and recovery functions
- Detailed logging system

## 📚 Learning Resources

- **[Quick Start Guide](./快速入门指南.md)** - Get started in 5 minutes
- **[Complete User Guide](./Pushdozer使用教程.md)** - Detailed feature explanations
- **[Configuration Reference](./配置参考.md)** - Advanced configuration options
- **[Multiplayer Guide](./MULTIPLAYER_FEATURES.md)** - Multiplayer features

## 🤝 Community Support

- **Modrinth**: [https://modrinth.com/mod/pushdozer](https://modrinth.com/mod/pushdozer)
- **GitHub**: Check the project page for latest information
- **Issue Reporting**: Welcome to submit bug reports and feature suggestions

---

**Pushdozer** - Making terrain editing simple and efficient, unleashing your creativity!
