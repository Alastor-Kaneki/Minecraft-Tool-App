package com.alastorkaneki.nullforge;

public enum TweakProvider {
    VANILLA(
            "Vanilla Tweaks",
            "Java Edition",
            "https://vanillatweaks.net",
            "https://github.com/VanillaTweaks/packs",
            "Vanilla Tweaks and its original creators retain ownership of all names, packs, artwork, and source material. NullForge Studio is an unofficial native client. The public GitHub repository contains source for some data packs, not the complete website catalog.",
            new String[]{"Data Packs", "Resource Packs", "Crafting Tweaks"}
    ),
    BEDROCK(
            "Bedrock Tweaks",
            "Bedrock Edition",
            "https://www.bedrocktweaks.net",
            "https://github.com/BedrockTweaks",
            "Bedrock Tweaks and its contributors retain ownership of all packs, names, artwork, and source material. NullForge Studio is an unofficial native client using the public Files catalog and repository.",
            new String[]{"Resource Packs", "Addons", "Crafting Tweaks"}
    ),
    BECOM(
            "BEComTweaks",
            "Bedrock Edition community ports",
            "https://becomtweaks.github.io",
            "https://github.com/BEComTweaks",
            "BEComTweaks and its contributors retain ownership of their ports, source, metadata, and artwork. Original Vanilla Tweaks concepts remain credited to Vanilla Tweaks and the individual creators listed upstream. NullForge Studio is an unofficial native client.",
            new String[]{"Resource Packs", "Behaviour Packs", "Crafting Tweaks"}
    );

    public final String label;
    public final String subtitle;
    public final String website;
    public final String repository;
    public final String credits;
    public final String[] sections;

    TweakProvider(String label, String subtitle, String website, String repository, String credits, String[] sections) {
        this.label = label;
        this.subtitle = subtitle;
        this.website = website;
        this.repository = repository;
        this.credits = credits;
        this.sections = sections;
    }
}
