package com.shoppulse.seed;

import java.math.BigDecimal;
import java.util.List;

/**
 * Hand-written demo catalogue (8 categories, 72 products) so the storefront and the
 * dashboard show believable names and prices rather than lorem ipsum.
 */
final class SeedCatalog {

    record ProductSeed(String name, String description, BigDecimal price) {
    }

    record CategorySeed(String name, List<ProductSeed> products) {
        String slug() {
            return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        }
    }

    private SeedCatalog() {
    }

    private static ProductSeed p(String name, String description, String price) {
        return new ProductSeed(name, description, new BigDecimal(price));
    }

    static List<CategorySeed> categories() {
        return List.of(
                new CategorySeed("Electronics", List.of(
                        p("Aurora Wireless Headphones", "Over-ear Bluetooth headphones with active noise cancelling and 40-hour battery life.", "129.99"),
                        p("Pulse Fitness Tracker", "Slim activity band with heart-rate monitoring and sleep tracking.", "59.99"),
                        p("Nimbus Smart Speaker", "Voice-controlled speaker with room-filling 360-degree sound.", "79.00"),
                        p("Volt 20000mAh Power Bank", "Fast-charging portable battery with dual USB-C ports.", "34.95"),
                        p("Lumen 4K Webcam", "Ultra-HD webcam with auto-framing and a built-in privacy shutter.", "89.50"),
                        p("Echo Buds Pro", "True wireless earbuds with transparency mode and wireless charging case.", "99.00"),
                        p("Slate 10-inch Tablet", "Lightweight tablet with a bright display for reading and streaming.", "249.00"),
                        p("Orbit Mechanical Keyboard", "Compact hot-swappable keyboard with tactile switches and RGB backlight.", "74.90"),
                        p("Glide Wireless Mouse", "Ergonomic rechargeable mouse with silent clicks.", "29.99"))),
                new CategorySeed("Home & Kitchen", List.of(
                        p("Ember Pour-Over Kettle", "Gooseneck electric kettle with precise temperature control.", "54.00"),
                        p("Hearth Cast Iron Skillet", "Pre-seasoned 12-inch skillet that lasts a lifetime.", "39.95"),
                        p("Brewmaster Coffee Grinder", "Burr grinder with 18 grind settings for espresso to French press.", "64.99"),
                        p("Haven Linen Duvet Cover", "Stonewashed linen duvet cover in a soft natural tone.", "119.00"),
                        p("Cedar Chopping Board", "Large end-grain cedar board with juice groove.", "44.50"),
                        p("Aroma Soy Candle Trio", "Three hand-poured soy candles in amber, fig and sea salt scents.", "27.00"),
                        p("Nova Air Fryer 5L", "Family-size air fryer with digital presets and a dishwasher-safe basket.", "109.99"),
                        p("Stoneware Dinner Set", "Sixteen-piece reactive-glaze dinnerware for four.", "89.00"),
                        p("Pantry Glass Storage Jars", "Set of six airtight jars with bamboo lids.", "32.50"))),
                new CategorySeed("Fashion", List.of(
                        p("Harbor Waxed Canvas Jacket", "Water-resistant field jacket with corduroy collar.", "149.00"),
                        p("Everyday Merino Sweater", "Soft crew-neck sweater in 100% fine merino wool.", "84.00"),
                        p("Trail Slim Chinos", "Stretch cotton chinos with a tailored, tapered fit.", "59.90"),
                        p("Classic White Sneakers", "Minimal leather sneakers with a cushioned insole.", "94.95"),
                        p("Solstice Linen Shirt", "Breathable button-up shirt for warm days.", "49.00"),
                        p("Highland Wool Scarf", "Oversized check scarf, brushed for extra warmth.", "36.00"),
                        p("Meridian Leather Belt", "Full-grain leather belt with a brushed brass buckle.", "42.00"),
                        p("Cloud Cotton T-Shirt 3-Pack", "Heavyweight organic cotton tees in black, white and slate.", "39.99"),
                        p("Atlas Canvas Backpack", "Roll-top backpack with a padded 15-inch laptop sleeve.", "69.00"))),
                new CategorySeed("Sports & Outdoors", List.of(
                        p("Summit 2-Person Tent", "Three-season tent that pitches in under five minutes.", "189.00"),
                        p("Flow Yoga Mat", "Non-slip 6mm mat with alignment markings.", "35.00"),
                        p("Ridge Insulated Bottle 750ml", "Double-wall steel bottle that keeps drinks cold for 24 hours.", "27.95"),
                        p("Stride Running Shoes", "Lightweight daily trainers with responsive foam.", "119.00"),
                        p("Peak Trekking Poles", "Pair of collapsible carbon trekking poles.", "79.50"),
                        p("Core Adjustable Dumbbells", "Space-saving dumbbells adjustable from 2 to 24 kg.", "229.00"),
                        p("Drift Dry Bag 20L", "Roll-top waterproof bag for kayaking and camping.", "24.99"),
                        p("Spark Headlamp 600", "Rechargeable 600-lumen headlamp with red night mode.", "31.00"),
                        p("Thermal Sleeping Bag", "Mummy bag rated to -5 C with compression sack.", "99.00"))),
                new CategorySeed("Beauty & Personal Care", List.of(
                        p("Glow Vitamin C Serum", "Brightening daily serum with 15% vitamin C.", "28.00"),
                        p("Calm Mineral Sunscreen SPF 50", "Lightweight reef-safe sunscreen with no white cast.", "19.50"),
                        p("Velvet Hydrating Face Cream", "Rich overnight cream with ceramides and hyaluronic acid.", "34.00"),
                        p("Forest Beard Oil", "Cedarwood and bergamot oil for a soft, tidy beard.", "16.99"),
                        p("Pure Clay Face Mask", "Detoxifying kaolin clay mask for oily skin.", "21.00"),
                        p("Sonic Electric Toothbrush", "Rechargeable brush with five modes and a two-minute timer.", "59.00"),
                        p("Silk Hair Towel Wrap", "Quick-dry microfibre wrap that reduces frizz.", "14.99"),
                        p("Botanical Shampoo & Conditioner Set", "Sulphate-free duo with argan and chamomile.", "29.50"),
                        p("Rose Hand Cream Trio", "Three travel-size hand creams in a gift tin.", "18.00"))),
                new CategorySeed("Books & Stationery", List.of(
                        p("The Quiet Algorithm", "A near-future thriller about a data scientist who finds a pattern too perfect to be real.", "16.99"),
                        p("Cooking With Fire", "Seventy-five recipes for the grill, the pit and the cast-iron pan.", "29.95"),
                        p("Atlas of Forgotten Places", "An illustrated tour of abandoned towns, rail lines and lighthouses.", "34.00"),
                        p("Dotted Hardcover Notebook", "A5 notebook with 192 pages of lay-flat 120gsm paper.", "17.50"),
                        p("Meridian Fountain Pen", "Brass-bodied fountain pen with a smooth medium nib.", "45.00"),
                        p("Colour Pencil Set of 72", "Soft-core pencils in a sturdy tin for artists and doodlers.", "38.00"),
                        p("Weekly Planner 2025", "Undated weekly planner with goals, habit tracker and notes pages.", "19.99"),
                        p("A Field Guide to Birds", "Compact guide with 600 colour plates and range maps.", "24.50"),
                        p("Sticky Note Bundle", "Twelve pads in assorted colours and sizes.", "9.99"))),
                new CategorySeed("Toys & Games", List.of(
                        p("Settlers of the Isle Board Game", "Strategy game of trading and building for three to four players.", "44.99"),
                        p("Brick Builder 1000-Piece Set", "Creative building set compatible with major brick brands.", "39.00"),
                        p("Mystery Escape Room Box", "Cooperative puzzle game that turns your table into an escape room.", "27.50"),
                        p("Plush Fox Companion", "Super-soft 30cm plush fox, machine washable.", "19.99"),
                        p("Wooden Train Starter Set", "Forty-piece beechwood railway set with two engines.", "49.00"),
                        p("Galaxy 500-Piece Jigsaw", "Glow-in-the-dark jigsaw puzzle of the night sky.", "16.00"),
                        p("RC Rock Crawler", "All-terrain remote control truck with rechargeable battery.", "64.95"),
                        p("Pocket Card Game Collection", "Ten classic card games in a travel tin.", "12.99"),
                        p("Junior Science Lab Kit", "Thirty safe experiments for curious eight-year-olds and up.", "32.00"))),
                new CategorySeed("Pet Supplies", List.of(
                        p("Paws Orthopedic Dog Bed", "Memory-foam bed with a removable washable cover.", "69.00"),
                        p("Whisker Interactive Cat Toy", "Motion-activated feather wand toy.", "18.99"),
                        p("Trailblazer Dog Harness", "No-pull reflective harness with padded chest plate.", "29.95"),
                        p("Premium Grain-Free Dog Food 5kg", "Chicken and sweet potato recipe for adult dogs.", "42.00"),
                        p("Purr Cat Scratching Tower", "Sisal-wrapped tower with a cosy hammock.", "74.00"),
                        p("Collapsible Travel Water Bowl", "Silicone bowl with a carabiner clip for walks.", "7.99"),
                        p("Aqua Pet Water Fountain", "Quiet filtered fountain that encourages hydration.", "36.50"),
                        p("Chew Dental Treats 30-Pack", "Vet-approved dental chews for medium dogs.", "15.99"),
                        p("Cozy Cat Cave Bed", "Felt cave bed that folds flat for washing.", "33.00"))));
    }
}
