# DiamondPortals

DiamondPortals is a Paper plugin prototype for block-themed dimensions.

## Current prototype

- Paper 1.21.x API
- Java 21
- A Nether-style rectangular frame can be built from `DIAMOND_BLOCK`.
- The frame is activated with flint and steel.
- The plugin replaces the frame interior with Nether portal blocks.
- A dedicated world is created on first use: `diamondportal_diamond_block`.
- The generated landscape uses only `DIAMOND_BLOCK` as its solid terrain material.
- Terrain has a non-flat height profile instead of being a superflat world.

## Planned next features

- Automatic return portals.
- Persistent portal-to-portal linking.
- Per-material portal types instead of only `DIAMOND_BLOCK`.
- Better terrain noise and caves while retaining one solid material.
- Safe arrival-location selection.
- Permissions and configurable material whitelist.

## Build

```bash
mvn clean package
```

The resulting JAR will be in `target/`.
