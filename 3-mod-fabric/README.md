# 3-mod-fabric - the Fabric mod

Thin wrapper: it plugs the shared engine ([`../5-shared-engine`](../5-shared-engine/)) into Fabric events.
The word lists come from [`../7-wordlists`](../7-wordlists/); the build copies them into the jar.
`gradle.properties` holds the versions; `../build-everything.sh` overrides them for each Minecraft version.
Status and known gaps: [`../6-guides/FABRIC-AND-FORGE-STATUS.md`](../6-guides/FABRIC-AND-FORGE-STATUS.md).
