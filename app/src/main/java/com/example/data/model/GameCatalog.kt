package com.example.data.model

enum class SkinRarity(val label: String, val colorHex: Long) {
    COMMON("Common", 0xFF90A4AE),
    RARE("Rare", 0xFF00E5FF),
    EPIC("Epic", 0xFFBB86FC),
    LEGENDARY("Legendary", 0xFFFFD54F),
    MYTHIC("Mythic", 0xFFFF3D57)
}

enum class SkinMaterial(val label: String, val shininess: Float, val emissiveBoost: Float, val metallic: Float) {
    MATTE_SCALE("Matte Scale", 16f, 0.08f, 0.1f),
    METALLIC("Brushed Metal", 72f, 0.15f, 0.85f),
    EMISSIVE_NEON("Neon Emissive", 48f, 0.65f, 0.25f),
    CRYSTAL("Faceted Crystal", 96f, 0.35f, 0.5f),
    IRIDESCENT("Iridescent Prism", 84f, 0.40f, 0.7f),
    MAGMA("Molten Magma", 32f, 0.75f, 0.2f),
    PLASMA("Arc Plasma", 64f, 0.80f, 0.4f),
    VOID("Obsidian Void", 40f, 0.22f, 0.6f)
}

enum class SnakeEffect(val label: String) {
    EMERALD_TRAIL("Emerald Trail"),
    CROWN_AURA("Royal Crown Aura"),
    FLAME_SPARKS("Flame Sparks"),
    FROST_MIST("Frost Mist"),
    ARC_LIGHTNING("Arc Lightning"),
    PRISM_SHIMMER("Prism Shimmer"),
    STARLIGHT_DUST("Starlight Dust"),
    SHADOW_SMOKE("Shadow Smoke"),
    TOXIC_SPORES("Toxic Spores"),
    CYBER_CIRCUIT("Cyber Pulse"),
    PHOENIX_FEATHERS("Phoenix Embers"),
    SOLAR_FLARE("Solar Halo")
}

data class SnakeSkin(
    val id: String,
    val name: String,
    val rarity: SkinRarity,
    val material: SkinMaterial,
    val effect: SnakeEffect,
    val primaryColor: Int,
    val secondaryColor: Int,
    val bellyColor: Int,
    val glowColor: Int,
    val hasCrown: Boolean = false,
    val hasHorns: Boolean = false,
    val unlockLevel: Int = 1,
    val unlockCostCoins: Int = 0,
    val isVipExclusive: Boolean = false,
    val description: String
)

enum class ArenaThemeId(val id: String) {
    JUNGLE("jungle_arena"),
    DESERT("desert_kingdom"),
    FROZEN("frozen_world"),
    VOLCANO("volcano_arena"),
    CYBER("cyber_city"),
    GALAXY("galaxy_arena"),
    CRYSTAL("crystal_kingdom"),
    UNDERWATER("underwater_world"),
    DARK_FOREST("dark_forest"),
    DRAGON_REALM("dragon_realm")
}

data class ArenaMap(
    val id: String,
    val name: String,
    val subtitle: String,
    val floorColor: Int,
    val gridColor: Int,
    val rimColor: Int,
    val skyTopColor: Int,
    val skyBottomColor: Int,
    val obstacleColor: Int,
    val arenaRadius: Float = 56f,
    val obstacleCount: Int = 6,
    val unlockLevel: Int = 1
)

enum class PowerUpType(
    val label: String,
    val shortCode: String,
    val durationSec: Float,
    val color: Int,
    val description: String
) {
    SPEED_BOOST("Speed Boost", "SPD", 6f, 0xFF00E5FF.toInt(), "Surges movement speed by 45% without draining boost"),
    SHIELD("Shield", "SHD", 7f, 0xFF69F0AE.toInt(), "Absorbs one lethal collision or body impact"),
    MAGNET("Magnet", "MAG", 8f, 0xFFFF4081.toInt(), "Pulls nearby energy orbs toward your cobra head"),
    DOUBLE_ENERGY("Double Energy", "2X", 9f, 0xFFFFD54F.toInt(), "Doubles all energy and score gained from orbs"),
    LIGHTNING("Lightning", "ZAP", 4f, 0xFF40C4FF.toInt(), "Discharges arc bolts slowing nearby rivals"),
    FREEZE("Freeze", "ICE", 5f, 0xFF80D8FF.toInt(), "Chills and slows all nearby enemy snakes"),
    INVISIBILITY("Invisibility", "INV", 5.5f, 0xFFB388FF.toInt(), "Cloaks your segments in semi-transparent stealth"),
    TELEPORT("Teleport", "TP", 1.5f, 0xFFE040FB.toInt(), "Phase-blinks safely forward across hazards"),
    TEMP_PROTECTION("Temp Protection", "PRT", 5f, 0xFFFFFFFF.toInt(), "Temporary invulnerability aura on entry")
}

enum class GameMode(
    val id: String,
    val title: String,
    val subtitle: String,
    val isLocallyPlayable: Boolean,
    val requiresOnlineBackend: Boolean,
    val defaultDurationSec: Int? = null,
    val allowRespawn: Boolean = true,
    val isTeamMode: Boolean = false,
    val hasShrinkingRing: Boolean = false
) {
    PRACTICE("PRACTICE", "Practice Mode", "Relaxed arena with Easy bots & instant respawn", true, false, null, true),
    AI_BATTLE("AI_BATTLE", "AI Battle", "Tactical 3D arena vs selectable AI difficulty", true, false, null, true),
    CLASSIC_SURVIVAL("CLASSIC_SURVIVAL", "Classic Survival", "Last cobra standing wins—no free respawns", true, false, null, false),
    ENDLESS_GROWTH("ENDLESS_GROWTH", "Endless Growth", "Grow without limits in an infinite energy arena", true, false, null, true),
    TIME_ATTACK("TIME_ATTACK", "Time Attack", "2-minute high-speed blitz for maximum score", true, false, 120, true),
    BATTLE_ROYALE("BATTLE_ROYALE", "Battle Royale", "Shrinking energy storm closes around the arena", true, false, null, false, false, true),
    TEAM_BATTLE("TEAM_BATTLE", "Team Battle", "Emerald vs Crimson 4v4 squad domination", true, false, 150, true, true),
    DAILY_CHALLENGE("DAILY_CHALLENGE", "Daily Challenge", "Seeded daily modifier run for bonus XP", true, false, 120, false),
    RANKED_MULTIPLAYER("RANKED_MULTIPLAYER", "Ranked Multiplayer", "Authoritative WebSocket ranked arena", false, true, 180, false),
    PRIVATE_FRIENDS_MATCH("PRIVATE_FRIENDS_MATCH", "Private Friends Match", "Join or host a room code with friends", false, true, 180, true),
    TOURNAMENT_MODE("TOURNAMENT_MODE", "Tournament Mode", "Scheduled bracket qualifiers via server", false, true, 180, false)
}

enum class AiDifficulty(val label: String, val reactionRate: Float, val aggression: Float, val boostTendency: Float) {
    EASY("Easy", 0.35f, 0.25f, 0.15f),
    NORMAL("Normal", 0.58f, 0.45f, 0.35f),
    HARD("Hard", 0.78f, 0.65f, 0.55f),
    EXPERT("Expert", 0.90f, 0.82f, 0.75f),
    MASTER("Master", 0.98f, 0.95f, 0.90f)
}

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val rewardXp: Int,
    val targetValue: Int,
    val metric: String
)

data class MissionDef(
    val id: String,
    val title: String,
    val rewardBpXp: Int,
    val targetValue: Int,
    val metric: String
)

data class BattlePassTier(
    val tier: Int,
    val requiredXp: Int,
    val freeRewardLabel: String,
    val premiumRewardLabel: String,
    val freeSkinId: String? = null,
    val premiumSkinId: String? = null
)

data class StoreOffer(
    val id: String,
    val title: String,
    val category: String,
    val priceDisplay: String,
    val productId: String,
    val skinId: String? = null,
    val isRealMoneyBilling: Boolean = true,
    val badge: String = ""
)

object GameCatalog {

    val skins: List<SnakeSkin> = buildList {
        add(SnakeSkin("royal_cobra", "Royal Cobra", SkinRarity.LEGENDARY, SkinMaterial.METALLIC, SnakeEffect.CROWN_AURA, 0xFF00E676.toInt(), 0xFFFFD54F.toInt(), 0xFFFFEA70.toInt(), 0xFF69F0AE.toInt(), hasCrown = true, unlockLevel = 1, description = "Crowned emerald sovereign of the arena."))
        add(SnakeSkin("fire_dragon", "Fire Dragon", SkinRarity.EPIC, SkinMaterial.MAGMA, SnakeEffect.FLAME_SPARKS, 0xFFFF5722.toInt(), 0xFFFFB300.toInt(), 0xFFFFE082.toInt(), 0xFFFF6D00.toInt(), hasHorns = true, unlockLevel = 2, unlockCostCoins = 500, description = "Molten scales forged in volcanic fire."))
        add(SnakeSkin("ice_serpent", "Ice Serpent", SkinRarity.RARE, SkinMaterial.CRYSTAL, SnakeEffect.FROST_MIST, 0xFF40C4FF.toInt(), 0xFFE1F5FE.toInt(), 0xFFB3E5FC.toInt(), 0xFF00E5FF.toInt(), unlockLevel = 1, unlockCostCoins = 300, description = "Glacial crystal armor with sub-zero trail."))
        add(SnakeSkin("electric_viper", "Electric Viper", SkinRarity.EPIC, SkinMaterial.PLASMA, SnakeEffect.ARC_LIGHTNING, 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFFE0F7FA.toInt(), 0xFF18FFFF.toInt(), unlockLevel = 3, unlockCostCoins = 650, description = "High-voltage plasma coils crackle along its spine."))
        add(SnakeSkin("golden_cobra", "Golden Cobra", SkinRarity.LEGENDARY, SkinMaterial.METALLIC, SnakeEffect.SOLAR_FLARE, 0xFFFFD54F.toInt(), 0xFFFFA000.toInt(), 0xFFFFF8E1.toInt(), 0xFFFFE082.toInt(), hasCrown = true, unlockLevel = 5, unlockCostCoins = 1200, description = "Solid 24-karat gilded serpent plating."))
        add(SnakeSkin("diamond_snake", "Diamond Snake", SkinRarity.LEGENDARY, SkinMaterial.CRYSTAL, SnakeEffect.PRISM_SHIMMER, 0xFFE0F7FA.toInt(), 0xFF80DEEA.toInt(), 0xFFFFFFFF.toInt(), 0xFFB2EBF2.toInt(), unlockLevel = 6, unlockCostCoins = 1500, description = "Refractive diamond facets gleaming in 3D."))
        add(SnakeSkin("rainbow_serpent", "Rainbow Serpent", SkinRarity.EPIC, SkinMaterial.IRIDESCENT, SnakeEffect.PRISM_SHIMMER, 0xFFFF4081.toInt(), 0xFF00E5FF.toInt(), 0xFFFFEA00.toInt(), 0xFF69F0AE.toInt(), unlockLevel = 4, unlockCostCoins = 900, description = "Chromatic prism scales cycling through the spectrum."))
        add(SnakeSkin("galaxy_dragon", "Galaxy Dragon", SkinRarity.MYTHIC, SkinMaterial.VOID, SnakeEffect.STARLIGHT_DUST, 0xFF5E35B1.toInt(), 0xFF00E5FF.toInt(), 0xFFD1C4E9.toInt(), 0xFF7C4DFF.toInt(), hasHorns = true, unlockLevel = 8, unlockCostCoins = 2200, description = "Cosmic nebula scales infused with starlight."))
        add(SnakeSkin("neon_snake", "Neon Snake", SkinRarity.RARE, SkinMaterial.EMISSIVE_NEON, SnakeEffect.CYBER_CIRCUIT, 0xFF00FF88.toInt(), 0xFFFF00AA.toInt(), 0xFF1A1A2E.toInt(), 0xFF00FF88.toInt(), unlockLevel = 2, unlockCostCoins = 350, description = "Synthwave hyper-neon tubing engineered for night arenas."))
        add(SnakeSkin("shadow_cobra", "Shadow Cobra", SkinRarity.EPIC, SkinMaterial.VOID, SnakeEffect.SHADOW_SMOKE, 0xFF212121.toInt(), 0xFF7E57C2.toInt(), 0xFF424242.toInt(), 0xFFB388FF.toInt(), unlockLevel = 4, unlockCostCoins = 800, description = "Stealth obsidian predator with violet aura."))
        add(SnakeSkin("crystal_snake", "Crystal Snake", SkinRarity.RARE, SkinMaterial.CRYSTAL, SnakeEffect.PRISM_SHIMMER, 0xFF64FFDA.toInt(), 0xFFB2DFDB.toInt(), 0xFFE0F2F1.toInt(), 0xFF1DE9B6.toInt(), unlockLevel = 2, unlockCostCoins = 400, description = "Translucent quartz prism segments."))
        add(SnakeSkin("toxic_viper", "Toxic Viper", SkinRarity.RARE, SkinMaterial.EMISSIVE_NEON, SnakeEffect.TOXIC_SPORES, 0xFF76FF03.toInt(), 0xFF33691E.toInt(), 0xFFCCFF90.toInt(), 0xFFB2FF59.toInt(), unlockLevel = 2, unlockCostCoins = 380, description = "Bio-luminescent venom glow."))
        add(SnakeSkin("robot_snake", "Robot Snake", SkinRarity.EPIC, SkinMaterial.METALLIC, SnakeEffect.CYBER_CIRCUIT, 0xFF90A4AE.toInt(), 0xFF00B0FF.toInt(), 0xFF37474F.toInt(), 0xFF00E5FF.toInt(), unlockLevel = 3, unlockCostCoins = 700, description = "Articulated titanium cyber-chassis."))
        add(SnakeSkin("ghost_serpent", "Ghost Serpent", SkinRarity.EPIC, SkinMaterial.PLASMA, SnakeEffect.SHADOW_SMOKE, 0xFFB0BEC5.toInt(), 0xFF80CBC4.toInt(), 0xFFECEFF1.toInt(), 0xFFA7FFEB.toInt(), unlockLevel = 4, unlockCostCoins = 750, description = "Spectral phantom gliding across the grid."))
        add(SnakeSkin("phoenix_cobra", "Phoenix Cobra", SkinRarity.MYTHIC, SkinMaterial.MAGMA, SnakeEffect.PHOENIX_FEATHERS, 0xFFFF3D00.toInt(), 0xFFFFD600.toInt(), 0xFFFFAB40.toInt(), 0xFFFF6E40.toInt(), hasCrown = true, unlockLevel = 9, unlockCostCoins = 2400, description = "Reborn in incandescent crimson and gold embers."))
        add(SnakeSkin("legendary_emperor_cobra", "Legendary Emperor Cobra", SkinRarity.MYTHIC, SkinMaterial.IRIDESCENT, SnakeEffect.CROWN_AURA, 0xFF00E676.toInt(), 0xFFFFD54F.toInt(), 0xFF00E5FF.toInt(), 0xFFFFD740.toInt(), hasCrown = true, hasHorns = true, unlockLevel = 12, isVipExclusive = true, description = "Apex mythic ruler adorned with emerald-gold crown."))

        // 34 additional original skin concepts to complete the 50-skin collection
        val extraSpecs = listOf(
            Triple("emerald_striker", "Emerald Striker", SkinRarity.COMMON) to Triple(0xFF00C853.toInt(), 0xFF1B5E20.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("cyber_fang", "Cyber Fang", SkinRarity.COMMON) to Triple(0xFF00B8D4.toInt(), 0xFF263238.toInt(), SkinMaterial.EMISSIVE_NEON),
            Triple("crimson_mamba", "Crimson Mamba", SkinRarity.COMMON) to Triple(0xFFD50000.toInt(), 0xFF4A0000.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("cobalt_python", "Cobalt Python", SkinRarity.COMMON) to Triple(0xFF2962FF.toInt(), 0xFF0D47A1.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("amber_adder", "Amber Adder", SkinRarity.COMMON) to Triple(0xFFFFAB00.toInt(), 0xFF5D4037.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("jade_anaconda", "Jade Anaconda", SkinRarity.RARE) to Triple(0xFF00BFA5.toInt(), 0xFF004D40.toInt(), SkinMaterial.METALLIC),
            Triple("desert_sidewinder", "Desert Sidewinder", SkinRarity.COMMON) to Triple(0xFFD7CCC8.toInt(), 0xFF8D6E63.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("midnight_krait", "Midnight Krait", SkinRarity.RARE) to Triple(0xFF1A237E.toInt(), 0xFF000051.toInt(), SkinMaterial.VOID),
            Triple("solar_asp", "Solar Asp", SkinRarity.EPIC) to Triple(0xFFFF9100.toInt(), 0xFFFFEA00.toInt(), SkinMaterial.EMISSIVE_NEON),
            Triple("glacier_boa", "Glacier Boa", SkinRarity.RARE) to Triple(0xFF84FFFF.toInt(), 0xFF006064.toInt(), SkinMaterial.CRYSTAL),
            Triple("lava_basilisk", "Lava Basilisk", SkinRarity.EPIC) to Triple(0xFFDD2C00.toInt(), 0xFF212121.toInt(), SkinMaterial.MAGMA),
            Triple("quantum_serpent", "Quantum Serpent", SkinRarity.LEGENDARY) to Triple(0xFF651FFF.toInt(), 0xFF00E5FF.toInt(), SkinMaterial.PLASMA),
            Triple("titan_mecha", "Titan Mecha", SkinRarity.EPIC) to Triple(0xFF546E7A.toInt(), 0xFFFF6D00.toInt(), SkinMaterial.METALLIC),
            Triple("sakura_ribbon", "Sakura Ribbon", SkinRarity.RARE) to Triple(0xFFFF80AB.toInt(), 0xFFFCE4EC.toInt(), SkinMaterial.IRIDESCENT),
            Triple("abyssal_leviathan", "Abyssal Leviathan", SkinRarity.LEGENDARY) to Triple(0xFF00695C.toInt(), 0xFF00E5FF.toInt(), SkinMaterial.VOID),
            Triple("aurora_wyrm", "Aurora Wyrm", SkinRarity.LEGENDARY) to Triple(0xFF00E676.toInt(), 0xFFAA00FF.toInt(), SkinMaterial.IRIDESCENT),
            Triple("plasma_hydra", "Plasma Hydra", SkinRarity.EPIC) to Triple(0xFFD500F9.toInt(), 0xFF00E5FF.toInt(), SkinMaterial.PLASMA),
            Triple("obsidian_king", "Obsidian King", SkinRarity.LEGENDARY) to Triple(0xFF121212.toInt(), 0xFFFFD54F.toInt(), SkinMaterial.METALLIC),
            Triple("coral_reef_eel", "Coral Reef Viper", SkinRarity.COMMON) to Triple(0xFFFF6E40.toInt(), 0xFF00BFA5.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("stealth_carbon", "Stealth Carbon", SkinRarity.RARE) to Triple(0xFF263238.toInt(), 0xFF00E676.toInt(), SkinMaterial.VOID),
            Triple("bronze_gladiator", "Bronze Gladiator", SkinRarity.COMMON) to Triple(0xFFA1887F.toInt(), 0xFF4E342E.toInt(), SkinMaterial.METALLIC),
            Triple("silver_sentinel", "Silver Sentinel", SkinRarity.RARE) to Triple(0xFFCFD8DC.toInt(), 0xFF455A64.toInt(), SkinMaterial.METALLIC),
            Triple("ruby_wyvern", "Ruby Wyvern", SkinRarity.EPIC) to Triple(0xFFC51162.toInt(), 0xFFFF80AB.toInt(), SkinMaterial.CRYSTAL),
            Triple("sapphire_drake", "Sapphire Drake", SkinRarity.EPIC) to Triple(0xFF2979FF.toInt(), 0xFF82B1FF.toInt(), SkinMaterial.CRYSTAL),
            Triple("amethyst_mystic", "Amethyst Mystic", SkinRarity.EPIC) to Triple(0xFFAA00FF.toInt(), 0xFFEA80FC.toInt(), SkinMaterial.CRYSTAL),
            Triple("venom_moss", "Venom Moss", SkinRarity.COMMON) to Triple(0xFF558B2F.toInt(), 0xFFAEEA00.toInt(), SkinMaterial.MATTE_SCALE),
            Triple("storm_tempest", "Storm Tempest", SkinRarity.EPIC) to Triple(0xFF37474F.toInt(), 0xFF40C4FF.toInt(), SkinMaterial.PLASMA),
            Triple("comet_tracer", "Comet Tracer", SkinRarity.RARE) to Triple(0xFF00B0FF.toInt(), 0xFFFFFFFF.toInt(), SkinMaterial.EMISSIVE_NEON),
            Triple("inferno_oni", "Inferno Oni", SkinRarity.LEGENDARY) to Triple(0xFFB71C1C.toInt(), 0xFFFFAB00.toInt(), SkinMaterial.MAGMA),
            Triple("celestial_monk", "Celestial Guardian", SkinRarity.LEGENDARY) to Triple(0xFFFFF59D.toInt(), 0xFF00E5FF.toInt(), SkinMaterial.IRIDESCENT),
            Triple("pixel_retro", "Retro Arcade", SkinRarity.RARE) to Triple(0xFF00E676.toInt(), 0xFFFFEA00.toInt(), SkinMaterial.EMISSIVE_NEON),
            Triple("magma_core", "Magma Core", SkinRarity.EPIC) to Triple(0xFF3E2723.toInt(), 0xFFFF3D00.toInt(), SkinMaterial.MAGMA),
            Triple("starlight_nebula", "Starlight Nebula", SkinRarity.MYTHIC) to Triple(0xFF311B92.toInt(), 0xFFFF4081.toInt(), SkinMaterial.PLASMA),
            Triple("imperial_jade", "Imperial Jade", SkinRarity.MYTHIC) to Triple(0xFF00E676.toInt(), 0xFFFFD54F.toInt(), SkinMaterial.CRYSTAL)
        )

        extraSpecs.forEachIndexed { idx, (idNameRarity, colorsMat) ->
            val (id, name, rarity) = idNameRarity
            val (primary, secondary, material) = colorsMat
            val effect = SnakeEffect.entries[idx % SnakeEffect.entries.size]
            add(
                SnakeSkin(
                    id = id,
                    name = name,
                    rarity = rarity,
                    material = material,
                    effect = effect,
                    primaryColor = primary,
                    secondaryColor = secondary,
                    bellyColor = 0xFFE8F5E9.toInt(),
                    glowColor = secondary,
                    hasCrown = rarity == SkinRarity.MYTHIC,
                    hasHorns = rarity == SkinRarity.LEGENDARY && idx % 2 == 0,
                    unlockLevel = 1 + (idx / 4),
                    unlockCostCoins = when (rarity) {
                        SkinRarity.COMMON -> 150
                        SkinRarity.RARE -> 350
                        SkinRarity.EPIC -> 700
                        SkinRarity.LEGENDARY -> 1400
                        SkinRarity.MYTHIC -> 2500
                    },
                    description = "${rarity.label} ${material.label} finish with ${effect.label}."
                )
            )
        }
    }

    val arenas: List<ArenaMap> = listOf(
        ArenaMap("jungle_arena", "Jungle Arena", "Ancient emerald temple ruins", 0xFF091B12.toInt(), 0xFF00E676.toInt(), 0xFF00E676.toInt(), 0xFF04140C.toInt(), 0xFF0E2C1B.toInt(), 0xFF1B5E20.toInt(), 54f, 6, 1),
        ArenaMap("desert_kingdom", "Desert Kingdom", "Sun-baked sandstone colosseum", 0xFF261C0E.toInt(), 0xFFFFB300.toInt(), 0xFFFFD54F.toInt(), 0xFF2B1B0A.toInt(), 0xFF5D3A1A.toInt(), 0xFF8D6E63.toInt(), 56f, 5, 1),
        ArenaMap("frozen_world", "Frozen World", "Glacial aurora ice shelf", 0xFF0A1D2B.toInt(), 0xFF40C4FF.toInt(), 0xFF80D8FF.toInt(), 0xFF07131F.toInt(), 0xFF15364E.toInt(), 0xFF81D4FA.toInt(), 56f, 6, 2),
        ArenaMap("volcano_arena", "Volcano Arena", "Obsidian caldera rim", 0xFF1E0B07.toInt(), 0xFFFF5722.toInt(), 0xFFFF3D00.toInt(), 0xFF1A0704.toInt(), 0xFF4A1308.toInt(), 0xFFBF360C.toInt(), 54f, 7, 2),
        ArenaMap("cyber_city", "Cyber City", "Neon synthwave megagrid", 0xFF090B1A.toInt(), 0xFF00E5FF.toInt(), 0xFFFF00AA.toInt(), 0xFF050714.toInt(), 0xFF141838.toInt(), 0xFF311B92.toInt(), 58f, 6, 1),
        ArenaMap("galaxy_arena", "Galaxy Arena", "Zero-G deep space platform", 0xFF0B071C.toInt(), 0xFF7C4DFF.toInt(), 0xFF00E5FF.toInt(), 0xFF03020A.toInt(), 0xFF1A1040.toInt(), 0xFF4527A0.toInt(), 60f, 5, 3),
        ArenaMap("crystal_kingdom", "Crystal Kingdom", "Prismatic quartz sanctuary", 0xFF0C1E21.toInt(), 0xFF64FFDA.toInt(), 0xFF1DE9B6.toInt(), 0xFF061618.toInt(), 0xFF12363B.toInt(), 0xFF00897B.toInt(), 56f, 7, 3),
        ArenaMap("underwater_world", "Underwater World", "Bioluminescent abyssal trench", 0xFF041624.toInt(), 0xFF00BFA5.toInt(), 0xFF18FFFF.toInt(), 0xFF020B14.toInt(), 0xFF072B42.toInt(), 0xFF006064.toInt(), 56f, 6, 4),
        ArenaMap("dark_forest", "Dark Forest", "Shadowed twilight canopy", 0xFF0B120C.toInt(), 0xFF76FF03.toInt(), 0xFF69F0AE.toInt(), 0xFF040805.toInt(), 0xFF142116.toInt(), 0xFF2E7D32.toInt(), 55f, 8, 4),
        ArenaMap("dragon_realm", "Dragon Realm", "Mythic sky citadel of the elders", 0xFF1A0B14.toInt(), 0xFFFFD54F.toInt(), 0xFFFF3D57.toInt(), 0xFF12060D.toInt(), 0xFF3B1224.toInt(), 0xFF880E4F.toInt(), 62f, 6, 5)
    )

    val achievements: List<AchievementDef> = listOf(
        AchievementDef("first_blood", "First Strike", "Eliminate your first rival cobra in the arena.", 100, 1, "kills"),
        AchievementDef("apex_hunter", "Apex Hunter", "Reach 25 cumulative eliminations.", 350, 25, "kills"),
        AchievementDef("high_roller", "Energy Baron", "Score 2,500+ points in a single match.", 250, 2500, "highScore"),
        AchievementDef("veteran_pilot", "Arena Veteran", "Complete 10 matches across any mode.", 300, 10, "matchesPlayed"),
        AchievementDef("crown_victor", "Arena Sovereign", "Win 5 matches in AI Battle or Survival.", 450, 5, "wins"),
        AchievementDef("skin_collector", "Scale Collector", "Unlock 5 or more cobra skins.", 200, 5, "skins")
    )

    val missions: List<MissionDef> = listOf(
        MissionDef("m_play_2", "Play 2 Arena Matches", 120, 2, "matchesPlayed"),
        MissionDef("m_score_1500", "Reach 1,500 Score in One Match", 180, 1500, "highScore"),
        MissionDef("m_kills_5", "Score 5 Eliminations", 150, 5, "kills"),
        MissionDef("m_win_1", "Win an AI Battle or Survival", 220, 1, "wins")
    )

    val battlePassTiers: List<BattlePassTier> = (1..10).map { tier ->
        BattlePassTier(
            tier = tier,
            requiredXp = tier * 200,
            freeRewardLabel = when {
                tier == 3 -> "Cyber Fang Crest"
                tier == 6 -> "Ice Serpent Unlock"
                tier == 10 -> "500 Bonus XP"
                else -> "+${tier * 50} Arena XP"
            },
            premiumRewardLabel = when {
                tier == 1 -> "Neon Glow Trail"
                tier == 4 -> "Electric Viper Skin"
                tier == 7 -> "Phoenix Embers Skin"
                tier == 10 -> "Galaxy Dragon Crown"
                else -> "Premium Crate T$tier"
            },
            freeSkinId = if (tier == 6) "ice_serpent" else null,
            premiumSkinId = when (tier) {
                4 -> "electric_viper"
                7 -> "phoenix_cobra"
                10 -> "galaxy_dragon"
                else -> null
            }
        )
    }

    val storeOffers: List<StoreOffer> = listOf(
        StoreOffer("offer_no_ads", "Remove Banner & Interstitial Ads", "Essentials", "$2.99", "cobradash_no_ads", badge = "POPULAR"),
        StoreOffer("offer_vip", "Royal Cobra VIP Pass (Monthly)", "VIP Membership", "$4.99 / mo", "cobradash_vip_monthly", skinId = "legendary_emperor_cobra", badge = "VIP"),
        StoreOffer("offer_bp", "Season 1 Battle Pass Premium", "Battle Pass", "$3.99", "cobradash_battle_pass_s1", badge = "SEASON 1"),
        StoreOffer("offer_coins_500", "500 Emerald Coins Pack", "Coin Packages", "$0.99", "cobradash_coins_500"),
        StoreOffer("offer_gems_120", "120 Cyber Gems Pack", "Gem Packages", "$1.99", "cobradash_gems_120"),
        StoreOffer("offer_bundle_dragon", "Dragon & Phoenix Cosmetic Bundle", "Cosmetic Bundles", "$5.99", "cobradash_bundle_dragon", skinId = "phoenix_cobra", badge = "SAVE 30%")
    )

    fun skinById(id: String): SnakeSkin = skins.firstOrNull { it.id == id } ?: skins.first()
    fun arenaById(id: String): ArenaMap = arenas.firstOrNull { it.id == id } ?: arenas.first()
}
