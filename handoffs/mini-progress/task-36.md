# Task 36: integration settings clean-up
- Package `engine.integration` merged into `engine.integrations` (PermissionSource, PlaceholderValues, ExternalProfiles moved).
- Renamed holders (no duplicate names): `EssentialsSettings` (was integrations.IntegrationSettings), `BanBedrockSettings` (was IntegrationsSettings), `PapiLuckPermsSettings` (was integration.IntegrationSettings).
- New single entry `integrations.IntegrationSettings.register(registry)`: one file header, order Essentials, bans+Bedrock, PlaceholderAPI/LuckPerms, extra (ProtocolLib etc.), plugin commands, nameplates, chat channels, network. Engine has one line.
- `NetworkSettings` stays in package `network` (called from the entry point). Only CONFIGURATION.md row order changed.
