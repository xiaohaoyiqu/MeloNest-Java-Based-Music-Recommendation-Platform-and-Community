


package com.haoran.music.common.util;

import java.text.Normalizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;







public final class ExternalCatalogSearchQuery {
    private static final Map<String, String> ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();
        add(aliases, "genshin impact", "原神", "げんしん", "原神インパクト", "원신");
        add(aliases, "overwatch", "守望先锋", "鬥陣特攻", "オーバーウォッチ", "오버워치");
        add(aliases, "pubg", "绝地求生", "絕地求生", "吃鸡", "배틀그라운드");
        add(aliases, "fall guys", "糖豆人", "フォールガイズ", "폴 가이즈");
        add(aliases, "lost ark", "失落的方舟", "ロストアーク", "로스트아크");
        add(aliases, "shooter", "射击", "射擊", "シューティング", "슈팅", "tir", "disparos", "schiessspiel", "tiro");
        add(aliases, "rpg", "角色扮演", "ロールプレイング", "롤플레잉", "jeu de role", "rollenspiel", "rol");
        add(aliases, "strategy", "策略", "战略", "戰略", "ストラテジー", "전략", "strategie", "estrategia", "strategia");
        add(aliases, "sports", "体育", "體育", "スポーツ", "스포츠", "sport", "deportes");
        add(aliases, "racing", "竞速", "競速", "赛车", "賽車", "レーシング", "레이싱", "course", "carreras");
        add(aliases, "fighting", "格斗", "格鬥", "対戦格闘", "격투", "combat", "lucha");
        add(aliases, "card", "卡牌", "カード", "카드", "cartes", "karten", "cartas");
        add(aliases, "battle royale", "大逃杀", "大逃殺", "バトルロイヤル", "배틀로얄");
        add(aliases, "social", "社交", "ソーシャル", "소셜", "sociale");
        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private ExternalCatalogSearchQuery() {
    }

    public static String canonicalize(String query) {
        String normalized = normalize(query);
        return ALIASES.getOrDefault(normalized, normalized);
    }

    public static boolean contains(Object value, String query) {
        String normalizedQuery = normalize(query);
        return !normalizedQuery.isEmpty() && normalize(value == null ? null : String.valueOf(value))
                .contains(normalizedQuery);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return compatible.replaceAll("\\s+", " ");
    }

    private static void add(Map<String, String> aliases, String canonical, String... localized) {
        aliases.put(normalize(canonical), normalize(canonical));
        for (String alias : localized) {
            aliases.put(normalize(alias), normalize(canonical));
        }
    }
}
