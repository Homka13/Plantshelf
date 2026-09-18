package com.plantshelf.app.data.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Built-in botanical encyclopedia repository containing detailed care profiles
 * for popular houseplants. 100% offline.
 */
public class PlantCatalogRepository {

    private static final List<CatalogPlant> CATALOG = new ArrayList<>();

    static {
        CATALOG.add(new CatalogPlant(
                "cat_monstera",
                "Монстера деліціоза",
                "Monstera deliciosa",
                "Ароїдні",
                "легка",
                "яскраве розсіяне",
                12000,
                8,
                14,
                14,
                "середня (50-60%)",
                "Пухкий агроперліт, кора хвойних, торф",
                "Отруйна для котів та собак (оксалати кальцію)",
                "Популярна ліана з величним розрізним листям. Не любить пряме пекуче сонце та застій води біля коріння."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_zamioculcas",
                "Заміокулькас замієлистий",
                "Zamioculcas zamiifolia",
                "Невибагливі",
                "дуже легка",
                "тінь або розсіяне",
                2500,
                14,
                25,
                30,
                "низька (сухе повітря переносить чудово)",
                "Дренований сукулентний ґрунт з великою часткою піску",
                "Слаботоксичний при потраплянні соку всередину",
                "«Доларове дерево» — одна з найбільш витривалих рослин. Накопичує вологу в бульбах, поливати лише після повного просихання."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_spathiphyllum",
                "Спатифілум («Жіноче щастя»)",
                "Spathiphyllum wallisii",
                "Квітучі",
                "легка",
                "півтінь або розсіяне",
                4000,
                5,
                9,
                14,
                "висока (любить обприскування)",
                "Універсальний слабокислий торф'яний субстрат",
                "Листя токсичне для домашніх улюбленців",
                "Очищує повітря в кімнаті. Сигналізує про спрагу опусканням листя, швидко піднімається після поливу."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_sansevieria",
                "Сансевієрія («Тещин язик»)",
                "Sansevieria trifasciata",
                "Невибагливі",
                "дуже легка",
                "будь-яке (від тіні до сонця)",
                3000,
                14,
                28,
                30,
                "низька",
                "Піщаний легкий субстрат з дренажем",
                "Небезпечна для тварин при жуванні",
                "Чемпіон з невибагливості та вироблення кисню вночі. Головне правило догляду — не заливати водою точку росту."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_ficus_elastica",
                "Фікус каучуконосний",
                "Ficus elastica",
                "Фікуси",
                "легка",
                "яскраве розсіяне",
                10000,
                7,
                14,
                14,
                "середня",
                "Поживний субстрат для фікусів з біогумусом",
                "Чумацький сік викликає подразнення шкіри",
                "Має щільне глянцеве шкірясте листя. Рекомендується регулярно протирати листя від пилу вологою серветкою."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_ficus_benjamina",
                "Фікус Бенджаміна",
                "Ficus benjamina",
                "Фікуси",
                "середня",
                "яскраве непряме",
                12000,
                6,
                12,
                14,
                "середня або висока",
                "Добре дренований ґрунт з вермикулітом",
                "Сік токсичний для котів",
                "Деревце з витонченими листочками. Не любить протягів і частих переміщень з місця на місце (може скинути листя)."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_epipremnum",
                "Епіпремнум золотистий",
                "Epipremnum aureum",
                "Ароїдні",
                "дуже легка",
                "розсіяне або півтінь",
                5000,
                6,
                10,
                14,
                "середня",
                "Легкий субстрат на основі верхового торфу",
                "Токсичний для тварин",
                "Швидкоросла ампельна ліана з красивими золотисто-зеленими серцеподібними листочками. Чудово вкорінюється у воді."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_crassula",
                "Красула («Грошове дерево»)",
                "Crassula ovata",
                "Сукуленти",
                "легка",
                "яскраве сонячне",
                18000,
                10,
                20,
                30,
                "сухе повітря",
                "Ґрунт для кактусів з великим вмістом гравію",
                "Слаботоксична для котів",
                "Деревоподібний сукулент з м'ясистими круглими листочками. Любить багато світла та рідкісний полив."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_phalaenopsis",
                "Орхідея Фаленопсис",
                "Phalaenopsis",
                "Квітучі",
                "середня",
                "м'яке розсіяне",
                8000,
                8,
                12,
                21,
                "підвищена (60%)",
                "Чиста кора сосни фракції 1-2 см, трохи моху сфагнуму",
                "Абсолютно безпечна для котів та собак",
                "Епіфітна красуня, яка цвіте кілька місяців поспіль. Полив методом замочування прозорого горщика на 15 хвилин."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_calathea",
                "Калатея зебрина",
                "Calathea zebrina",
                "Марантові",
                "складна",
                "м'яка півтінь",
                4000,
                4,
                7,
                14,
                "висока (від 65%)",
                "Слабокислий повітропроникний субстрат",
                "Безпечна для домашніх тварин (Pet-friendly)",
                "«Молитовна рослина» з неймовірними оксамитовими візерунками на листі. Потребує високої вологості та м'якої теплої води."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_dracaena",
                "Драцена маргіната",
                "Dracaena marginata",
                "Пальми",
                "легка",
                "розсіяне світло",
                6000,
                7,
                14,
                21,
                "середня",
                "Універсальний субстрат з керамзитовим дренажем",
                "Токсична для котів",
                "Струнке псевдопальмове деревце з вузьким листям з червоною облямівкою. Стійка до сухого повітря квартир."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_anthurium",
                "Антуріум («Чоловіче щастя»)",
                "Anthurium andraeanum",
                "Квітучі",
                "середня",
                "яскраве розсіяне",
                9000,
                6,
                10,
                14,
                "висока",
                "Кислий пухкий субстрат як для орхідей/ароїдних",
                "Отруйний для котів і собак",
                "Вражаючі глянцеві воскові квіти-покривала яскраво-червоного кольору. Любить тепло і регулярний помірний полив."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_aloe",
                "Алое вера",
                "Aloe barbadensis",
                "Сукуленти",
                "дуже легка",
                "яскраве сонячне",
                20000,
                14,
                30,
                30,
                "сухе повітря",
                "Суміш піску, перліту та дернової землі",
                "Шкірка листя небезпечна для тварин",
                "Відома цілюща рослина. Накопичує гель всередині листя, практично не потребує поливу взимку."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_chlorophytum",
                "Хлорофітум чубатий",
                "Chlorophytum comosum",
                "Невибагливі",
                "дуже легка",
                "будь-яке світло",
                5000,
                5,
                10,
                14,
                "будь-яка",
                "Звичайний універсальний ґрунт",
                "Абсолютно безпечний для котів (Pet-friendly)",
                "Номер один за здатністю очищати повітря від формальдегіду та чадного газу. Випускає вуса з маленькими розетками."
        ));

        CATALOG.add(new CatalogPlant(
                "cat_monstera_adansonii",
                "Монстера Адансона (Мавпяча маска)",
                "Monstera adansonii",
                "Ароїдні",
                "легка",
                "яскраве розсіяне",
                10000,
                6,
                12,
                14,
                "середня або висока",
                "Пухкий субстрат з кокосовими чипсами та корою",
                "Токсична для тварин",
                "Швидкоросла ліана з красивими овальними отворами по всьому полотну листка. Чудово виглядає на моховій опорі."
        ));
    }

    public static List<CatalogPlant> getAllPlants() {
        return Collections.unmodifiableList(CATALOG);
    }

    public static List<String> getCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("Всі");
        categories.add("Невибагливі");
        categories.add("Ароїдні");
        categories.add("Фікуси");
        categories.add("Сукуленти");
        categories.add("Квітучі");
        categories.add("Пальми");
        categories.add("Марантові");
        return categories;
    }

    public static List<CatalogPlant> filter(String query, String category) {
        List<CatalogPlant> filtered = new ArrayList<>();
        String q = query != null ? query.trim().toLowerCase() : "";
        boolean filterCat = category != null && !category.isEmpty() && !"Всі".equalsIgnoreCase(category);

        for (CatalogPlant p : CATALOG) {
            if (filterCat && !category.equalsIgnoreCase(p.getCategory())) {
                continue;
            }

            if (!q.isEmpty()) {
                boolean matchesName = p.getName().toLowerCase().contains(q);
                boolean matchesLatin = p.getLatin().toLowerCase().contains(q);
                boolean matchesDesc = p.getDescription().toLowerCase().contains(q);
                if (!matchesName && !matchesLatin && !matchesDesc) {
                    continue;
                }
            }

            filtered.add(p);
        }
        return filtered;
    }
}
