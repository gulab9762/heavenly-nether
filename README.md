# Mod Template

Clean multiloader mod template for Minecraft 26.3 supporting Fabric, NeoForge, and Forge.

## Supported Mod Loaders
- **Fabric** (Fabric 0.160.6+26.3)
- **NeoForge** (NeoForge 26.3.0.1-beta)
- **MinecraftForge** (Forge 66.0.10)

## Building
- Build Universal JAR (Fabric + NeoForge + Forge combined):
  ```powershell
  .\gradlew.bat universalJar
  ```
- Build Fabric only:
  ```powershell
  .\gradlew.bat buildFabric
  ```
- Build NeoForge only:
  ```powershell
  .\gradlew.bat buildNeoForge
  ```
- Build Forge only:
  ```powershell
  .\gradlew.bat buildForge
  ```

## Deploying
Deploy the compiled JAR to MultiMC and Downloads:
```powershell
.\scripts\deploy.ps1 -Loader universal
```
