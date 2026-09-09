import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const output = join(root, "src", "main", "resources", "assets", "fpv", "models", "block");
mkdirSync(output, { recursive: true });

const directions = ["down", "up", "north", "south", "west", "east"];

function faces(texture, overrides = {}) {
  return Object.fromEntries(directions.map(d => [d, { texture: overrides[d] ?? `#${texture}` }]));
}

function cube(from, to, texture, rotation, overrides) {
  const e = { from, to };
  if (rotation) e.rotation = rotation;
  e.faces = faces(texture, overrides);
  return e;
}

function rot(axis, angle, origin = [8, 8, 8], rescale = false) {
  return { origin, axis, angle, rescale };
}

function model(textures, elements) {
  return { parent: "minecraft:block/block", textures, elements };
}

function write(name, value) {
  writeFileSync(join(output, `${name}.json`), JSON.stringify(value, null, 2) + "\n");
}

const T = {
  steel: "minecraft:block/iron_block",
  panel: "minecraft:block/deepslate_tiles",
  plate: "minecraft:block/polished_deepslate",
  dark: "minecraft:block/black_concrete",
  gray: "minecraft:block/gray_concrete",
  light: "minecraft:block/light_gray_concrete",
  white: "minecraft:block/white_concrete",
  copper: "minecraft:block/copper_block",
  olive: "minecraft:block/green_concrete",
  green: "minecraft:block/lime_concrete",
  red: "minecraft:block/red_concrete",
  yellow: "minecraft:block/yellow_concrete",
  orange: "minecraft:block/orange_concrete",
  blue: "minecraft:block/light_blue_concrete",
  purple: "minecraft:block/purple_concrete",
  obsidian: "minecraft:block/crying_obsidian",
  lamp: "minecraft:block/redstone_lamp_on",
  glow: "minecraft:block/sea_lantern"
};

const rim = (y1, y2, tex, inset = 0, wall = 1) => [
  cube([inset, y1, inset], [16 - inset, y2, inset + wall], tex),
  cube([inset, y1, 16 - inset - wall], [16 - inset, y2, 16 - inset], tex),
  cube([inset, y1, inset + wall], [inset + wall, y2, 16 - inset - wall], tex),
  cube([16 - inset - wall, y1, inset + wall], [16 - inset, y2, 16 - inset - wall], tex)
];

const legs = (y1, y2, tex, inset = 1, size = 3) => [
  [inset, inset], [16 - inset - size, inset],
  [inset, 16 - inset - size], [16 - inset - size, 16 - inset - size]
].map(([x, z]) => cube([x, y1, z], [x + size, y2, z + size], tex));

function table(woodSide, woodTop) {
  return [
    ...legs(0, 12, woodSide),
    cube([2, 3, 2], [14, 4.6, 14], woodSide),
    ...rim(9, 12, woodSide, 1, 1.2),
    cube([0, 12, 0], [16, 14, 16], woodTop),
    cube([2.5, 9.2, 0.55], [13.5, 11.8, 1.0], woodSide),
    cube([6.4, 10.1, 0.15], [9.6, 10.9, 0.6], "steel")
  ];
}

write("workbench", model({
  particle: "minecraft:block/oak_planks",
  wood: "minecraft:block/oak_planks",
  top: "minecraft:block/oak_planks", steel: T.steel, dark: T.dark,
  board: T.green, copper: T.copper, red: T.red, glow: T.glow
}, [
  ...table("wood", "top"),
  cube([0, 14, 13.6], [16, 16, 15.4], "wood"),
  cube([2.2, 14.4, 13.2], [2.8, 15.6, 13.6], "steel"),
  cube([4.0, 14.4, 13.2], [4.8, 15.4, 13.6], "dark"),
  cube([6.0, 14.6, 13.2], [7.2, 15.4, 13.6], "steel"),
  cube([2, 14, 3.5], [5.2, 15.6, 7.5], "steel"),
  cube([2.6, 15.6, 4.1], [4.6, 16, 6.9], "dark"),
  cube([6.6, 14, 2.4], [12.6, 14.5, 8.2], "board"),
  cube([7.4, 14.5, 3.2], [8.6, 14.9, 4.4], "dark"),
  cube([9.6, 14.5, 3.2], [10.6, 14.9, 4.0], "red"),
  cube([8.2, 14.5, 6.0], [11.4, 14.8, 6.8], "copper"),
  cube([9.6, 14, 9.6], [13.4, 15.9, 13.4], "copper"),
  cube([10.4, 14, 9.2], [12.6, 15.9, 13.8], "dark"),
  cube([5.4, 14, 9.4], [6.2, 14.8, 13.4], "steel"),
  cube([6.2, 14, 10.6], [8.4, 14.9, 12.2], "dark")
]));

write("assembly_station", model({
  particle: T.panel, body: T.panel, deck: T.plate, steel: T.steel, dark: T.dark,
  carbon: T.dark, board: T.green, orange: T.orange, red: T.red, screen: T.glow, copper: T.copper
}, [
  ...legs(0, 11.5, "steel"),
  cube([1.5, 2.6, 1.5], [14.5, 4.2, 14.5], "body"),
  cube([2.4, 4.2, 2.4], [13.6, 5.0, 13.6], "dark"),
  cube([2.6, 5.0, 2.6], [6.4, 8.4, 6.4], "orange"),
  cube([9.6, 5.0, 2.6], [13.4, 8.4, 6.4], "copper"),
  ...rim(8.6, 11.5, "body", 1, 1.2),
  cube([0, 11.5, 0], [16, 13.4, 16], "deck"),
  cube([1, 13.4, 12.6], [15, 14.2, 15], "body"),
  cube([1.2, 13.4, 1.2], [1.9, 14.6, 12.6], "body"),
  cube([14.1, 13.4, 1.2], [14.8, 14.6, 12.6], "body"),
  cube([6.4, 13.4, 2.4], [9.6, 14.1, 12.4], "carbon"),
  cube([2.4, 13.4, 6.4], [13.6, 14.1, 9.6], "carbon"),
  cube([6.8, 14.1, 6.8], [9.2, 14.8, 9.2], "board"),
  ...[[7.2, 2.6], [7.2, 12.0], [2.6, 7.2], [12.0, 7.2]].map(([x, z]) =>
    cube([x, 14.1, z], [x + 1.6, 15.3, z + 1.6], "steel")),
  ...[[7.2, 2.6], [7.2, 12.0], [2.6, 7.2], [12.0, 7.2]].map(([x, z]) =>
    cube([x + 0.3, 15.3, z + 0.3], [x + 1.3, 15.7, z + 1.3], "red")),
  cube([2.6, 13.4, 13.0], [6.6, 13.9, 14.6], "board"),
  cube([9.4, 13.7, 12.9], [14.4, 16.0, 13.6], "dark", rot("x", -22.5, [11.9, 13.7, 14.0])),
  cube([9.8, 14.0, 12.7], [14.0, 15.7, 12.9], "screen", rot("x", -22.5, [11.9, 13.7, 14.0]))
]));

write("control_station", model({
  particle: T.panel, body: T.panel, deck: T.plate, steel: T.steel, dark: T.dark,
  screen: T.glow, blue: T.blue, green: T.green, red: T.red, gray: T.gray
}, [
  cube([0.5, 0, 2], [3.2, 9, 14.5], "body"),
  cube([12.8, 0, 2], [15.5, 9, 14.5], "body"),
  cube([3.2, 0, 12.5], [12.8, 9, 14.5], "body"),
  cube([3.2, 1.2, 11.6], [12.8, 2.4, 12.5], "steel"),
  cube([3.4, 3.0, 12.2], [12.6, 6.4, 12.5], "dark"),
  cube([4.0, 3.6, 11.9], [7.4, 5.8, 12.2], "green"),
  cube([8.4, 3.6, 11.9], [11.8, 5.8, 12.2], "blue"),
  cube([0, 9, 0], [16, 11, 16], "deck"),
  cube([0, 11, 15.2], [16, 11.6, 16], "body"),
  cube([7, 11, 12.6], [9, 12.2, 14.6], "steel"),
  cube([2.2, 11.4, 13.0], [13.8, 16, 14.2], "dark"),
  cube([2.9, 12.1, 12.75], [13.1, 15.4, 13.0], "screen"),
  cube([4.2, 11, 5.6], [11.8, 11.8, 10.2], "dark"),
  cube([4.6, 11.8, 6.0], [11.4, 12.0, 9.8], "gray"),
  cube([2.0, 11, 2.2], [7.2, 12.6, 5.6], "dark"),
  cube([2.4, 12.6, 2.6], [6.8, 12.9, 5.2], "steel"),
  cube([3.1, 12.9, 3.5], [3.9, 14.1, 4.3], "steel"),
  cube([5.3, 12.9, 3.5], [6.1, 14.1, 4.3], "steel"),
  cube([2.6, 12.9, 2.2], [3.4, 13.6, 2.6], "steel"),
  cube([9.0, 11, 2.4], [13.6, 11.5, 5.0], "body"),
  cube([9.4, 11.5, 2.8], [13.2, 11.8, 4.6], "red"),
  cube([13.9, 11, 6.0], [15.4, 12.4, 10.4], "dark"),
  cube([14.3, 12.4, 6.4], [15.0, 15.2, 7.1], "steel")
]));

write("kamikaze_station", model({
  particle: T.olive, body: T.olive, dark: T.dark, steel: T.steel, wood: "minecraft:block/stripped_oak_log",
  red: T.red, yellow: T.yellow, green: T.green, screen: T.glow, copper: T.copper
}, [
  cube([0, 0, 0], [16, 1.4, 3.0], "wood"),
  cube([0, 0, 6.5], [16, 1.4, 9.5], "wood"),
  cube([0, 0, 13.0], [16, 1.4, 16], "wood"),
  cube([0.5, 0, 3.0], [3.0, 1.0, 13.0], "wood"),
  cube([13.0, 0, 3.0], [15.5, 1.0, 13.0], "wood"),
  cube([0.5, 1.4, 0.5], [15.5, 2.4, 15.5], "body"),
  ...rim(2.4, 9.4, "body", 0.5, 1.1),
  cube([1.4, 2.4, 1.4], [1.8, 9.4, 14.6], "dark"),
  cube([14.2, 2.4, 1.4], [14.6, 9.4, 14.6], "dark"),
  cube([1.8, 8.5, 1.4], [14.2, 9.4, 1.8], "dark"),
  ...[[3.2, 3.4], [8.6, 3.4], [3.2, 9.0], [8.6, 9.0]].map(([x, z]) =>
    cube([x, 2.4, z], [x + 4.2, 7.4, z + 3.6], "dark")),
  ...[[3.2, 3.4], [8.6, 3.4], [3.2, 9.0], [8.6, 9.0]].map(([x, z]) =>
    cube([x + 0.6, 7.4, z + 0.6], [x + 3.6, 8.4, z + 3.0], "red")),
  ...[[3.2, 3.4], [8.6, 3.4], [3.2, 9.0], [8.6, 9.0]].map(([x, z]) =>
    cube([x + 1.4, 8.4, z + 1.2], [x + 2.8, 9.0, z + 2.4], "yellow")),
  cube([1.2, 9.4, 14.0], [14.8, 10.2, 15.2], "steel"),
  cube([1.2, 9.5, 3.0], [14.8, 10.3, 14.4], "body", rot("x", 22.5, [8, 9.9, 14.4])),
  cube([1.8, 10.3, 3.6], [14.2, 10.6, 13.8], "dark", rot("x", 22.5, [8, 9.9, 14.4])),
  cube([5.2, 10.6, 5.4], [10.8, 10.9, 8.6], "yellow", rot("x", 22.5, [8, 9.9, 14.4])),
  cube([2.6, 3.6, 0.05], [13.4, 7.8, 0.55], "dark"),
  cube([3.2, 5.4, -0.05], [7.6, 7.2, 0.1], "screen"),
  cube([8.6, 6.2, -0.05], [9.8, 7.2, 0.1], "green"),
  cube([10.4, 6.2, -0.05], [11.6, 7.2, 0.1], "red"),
  cube([8.6, 4.2, -0.05], [12.4, 5.2, 0.1], "copper")
]));

write("drone_nest", model({
  particle: T.plate, base: T.plate, deck: T.dark, steel: T.steel, body: T.panel,
  white: T.white, yellow: T.yellow, glow: T.glow, copper: T.copper, red: T.red, screen: T.blue
}, [
  cube([0, 0, 0], [16, 1.6, 16], "base"),
  cube([0.6, 1.6, 0.6], [15.4, 2.8, 15.4], "deck"),
  cube([4.4, 2.8, 3.4], [5.9, 3.1, 12.6], "white"),
  cube([10.1, 2.8, 3.4], [11.6, 3.1, 12.6], "white"),
  cube([5.9, 2.8, 7.2], [10.1, 3.1, 8.8], "white"),
  ...[[0.9, 0.9], [13.4, 0.9], [0.9, 13.4], [13.4, 13.4]].map(([x, z]) =>
    cube([x, 2.8, z], [x + 1.7, 6.2, z + 1.7], "steel")),
  ...[[0.9, 0.9], [13.4, 0.9], [0.9, 13.4], [13.4, 13.4]].map(([x, z]) =>
    cube([x - 0.15, 6.2, z - 0.15], [x + 1.85, 7.4, z + 1.85], "yellow")),
  ...[[0.9, 0.9], [13.4, 0.9], [0.9, 13.4], [13.4, 13.4]].map(([x, z]) =>
    cube([x + 0.3, 7.4, z + 0.3], [x + 1.4, 8.0, z + 1.4], "glow")),
  cube([3.4, 2.8, 12.4], [12.6, 10.4, 15.2], "body"),
  cube([3.9, 10.4, 12.9], [12.1, 11.0, 14.9], "steel"),
  cube([4.4, 5.6, 12.1], [11.6, 9.4, 12.4], "screen"),
  cube([4.4, 3.4, 12.1], [6.2, 4.8, 12.4], "red"),
  cube([7.0, 3.4, 12.1], [8.8, 4.8, 12.4], "glow"),
  cube([6.8, 11.0, 13.2], [9.2, 12.2, 14.6], "steel"),
  cube([7.6, 12.2, 13.6], [8.4, 15.2, 14.2], "steel"),
  cube([6.4, 15.2, 13.4], [9.6, 15.8, 14.4], "red"),
  cube([2.6, 2.8, 5.4], [3.6, 3.6, 10.6], "copper"),
  cube([12.4, 2.8, 5.4], [13.4, 3.6, 10.6], "copper")
]));

const detector = lens => model({
  particle: T.plate, base: T.plate, steel: T.steel, body: T.panel, dark: T.dark,
  copper: T.copper, lens, glow: T.glow
}, [
  cube([2.6, 0, 2.6], [13.4, 1.6, 13.4], "base"),
  cube([4.0, 1.6, 4.0], [12.0, 2.6, 12.0], "body"),
  cube([4.6, 2.6, 4.6], [11.4, 5.4, 11.4], "body"),
  cube([5.2, 3.2, 4.3], [10.8, 4.8, 4.6], "dark"),
  cube([5.8, 3.6, 4.1], [7.2, 4.4, 4.3], "lens"),
  cube([6.9, 5.4, 6.9], [9.1, 10.4, 9.1], "steel"),
  cube([6.4, 6.6, 6.4], [9.6, 7.2, 9.6], "dark"),
  cube([7.55, 10.4, 1.8], [8.45, 11.2, 14.2], "steel"),
  ...[[2.6, 5.6], [4.8, 4.6], [6.9, 3.6], [9.0, 2.8], [11.1, 2.2]].map(([z, half]) =>
    cube([8 - half, 10.55, z], [8 + half, 11.05, z + 0.55], "copper")),
  cube([12.6, 10.2, 2.4], [13.4, 11.4, 5.2], "copper"),
  cube([6.2, 11.2, 6.2], [9.8, 12.4, 9.8], "body"),
  cube([6.6, 12.4, 6.6], [9.4, 14.0, 9.4], "lens"),
  cube([7.1, 14.0, 7.1], [8.9, 14.8, 8.9], "steel"),
  cube([7.4, 14.8, 7.4], [8.6, 15.6, 8.6], "glow")
]);

write("drone_detector", detector(T.gray));
write("drone_detector_active", detector(T.lamp));

const jammer = emit => model({
  particle: T.panel, body: T.panel, base: T.plate, steel: T.steel, dark: T.dark,
  purple: T.purple, emit, copper: T.copper
}, [
  cube([1.6, 0, 1.6], [14.4, 1.2, 14.4], "base"),
  cube([2.6, 1.2, 2.6], [13.4, 7.2, 13.4], "body"),
  cube([3.2, 2.0, 2.3], [12.8, 6.4, 2.6], "dark"),
  ...[3.0, 4.4, 5.8].map(y => cube([3.8, y, 2.1], [12.2, y + 0.7, 2.3], "purple")),
  cube([3.2, 7.2, 3.2], [12.8, 8.0, 12.8], "steel"),
  cube([6.8, 8.0, 6.8], [9.2, 10.2, 9.2], "steel"),
  cube([6.2, 10.2, 6.2], [9.8, 11.2, 9.8], "emit"),
  cube([7.4, 10.2, 7.4], [8.6, 15.6, 8.6], "steel"),
  cube([7.1, 15.6, 7.1], [8.9, 16.0, 8.9], "emit"),
  ...[
    [[4.6, 7.4], [5.8, 8.6], "z", 22.5, [5.2, 10.2, 8]],
    [[10.2, 7.4], [11.4, 8.6], "z", -22.5, [10.8, 10.2, 8]],
    [[7.4, 4.6], [8.6, 5.8], "x", -22.5, [8, 10.2, 5.2]],
    [[7.4, 10.2], [8.6, 11.4], "x", 22.5, [8, 10.2, 10.8]]
  ].flatMap(([a, b, axis, ang, org]) => [
    cube([a[0], 10.2, a[1]], [b[0], 14.4, b[1]], "steel", rot(axis, ang, org)),
    cube([a[0] - 0.3, 14.4, a[1] - 0.3], [b[0] + 0.3, 15.2, b[1] + 0.3], "emit", rot(axis, ang, org)),
    cube([a[0] + 0.2, 10.0, a[1] + 0.2], [b[0] - 0.2, 10.4, b[1] - 0.2], "dark", rot(axis, ang, org))
  ]),
  cube([4.6, 9.4, 7.4], [11.4, 10.2, 8.6], "steel"),
  cube([7.4, 9.4, 4.6], [8.6, 10.2, 11.4], "steel"),
  cube([12.9, 1.2, 5.6], [13.9, 6.4, 6.6], "copper")
]);

write("drone_jammer", jammer(T.gray));
write("drone_jammer_active", jammer(T.glow));

const chargingStation = live => model({
  particle: T.plate, base: T.plate, body: T.panel, steel: T.steel, dark: T.dark,
  copper: T.copper, live, yellow: T.yellow
}, [
  cube([0, 0, 0], [16, 2, 16], "base"),
  cube([1, 2, 1], [15, 13, 15], "body"),
  cube([1.4, 12.2, 1.4], [14.6, 13.0, 14.6], "steel"),
  ...[0, 1, 2].flatMap(c => [0, 1].map(r =>
    cube([2.2 + c * 4.1, 3.4 + r * 4.4, 0.7], [5.6 + c * 4.1, 6.8 + r * 4.4, 1.2], "dark"))),
  ...[0, 1, 2].flatMap(c => [0, 1].map(r =>
    cube([2.8 + c * 4.1, 3.9 + r * 4.4, 0.45], [5.0 + c * 4.1, 4.6 + r * 4.4, 0.75], "live"))),
  cube([0.5, 4.0, 4.0], [1.0, 10.0, 10.0], "dark"),
  cube([0.3, 5.4, 5.4], [0.85, 8.6, 8.6], "copper"),
  ...[3.0, 5.4, 7.8, 10.2].map(z => cube([15.0, 3.0, z], [15.6, 11.5, z + 1.2], "steel")),
  cube([1, 13, 10], [15, 16, 15], "body"),
  cube([1.6, 13.4, 9.5], [14.4, 15.6, 10.0], "dark"),
  cube([2.4, 14.0, 9.15], [7.4, 15.1, 9.55], "live"),
  cube([8.2, 14.2, 9.15], [9.4, 14.9, 9.55], "yellow"),
  cube([10.0, 14.2, 9.15], [11.2, 14.9, 9.55], "copper"),
  cube([1, 13, 1], [15, 13.8, 10], "steel"),
  cube([3.0, 13.8, 2.4], [13.0, 14.3, 8.6], "dark"),
  ...[3.6, 6.2, 8.8, 11.4].map(x => cube([x, 14.3, 3.2], [x + 1.6, 14.7, 7.8], "live"))
]);

write("charging_station", chargingStation(T.gray));
write("charging_station_active", chargingStation(T.glow));

const pedestal = (accent, glyph) => model({
  particle: T.plate, base: T.plate, body: T.panel, steel: T.steel, dark: T.dark,
  accent, glyph, glow: T.glow
}, [
  cube([0.8, 0, 0.8], [15.2, 1.8, 15.2], "base"),
  cube([2.0, 1.8, 2.0], [14.0, 2.6, 14.0], "steel"),
  cube([2.4, 2.6, 2.4], [13.6, 7.4, 13.6], "body"),
  cube([2.1, 3.4, 2.1], [13.9, 4.2, 13.9], "accent"),
  cube([1.9, 7.4, 1.9], [14.1, 8.2, 14.1], "steel"),
  ...[[2.2, 2.2], [11.8, 2.2], [2.2, 11.8], [11.8, 11.8]].map(([x, z]) =>
    cube([x, 8.2, z], [x + 2.0, 11.4, z + 2.0], "steel")),
  ...[[2.2, 2.2], [11.8, 2.2], [2.2, 11.8], [11.8, 11.8]].map(([x, z]) =>
    cube([x + 0.15, 11.4, z + 0.15], [x + 1.85, 12.4, z + 1.85], "accent")),
  cube([3.4, 8.2, 3.4], [12.6, 9.0, 12.6], "dark"),
  cube([4.4, 9.0, 4.4], [11.6, 9.6, 11.6], "glyph"),
  cube([6.4, 9.6, 6.4], [9.6, 13.4, 9.6], "body"),
  cube([6.0, 13.4, 6.0], [10.0, 14.2, 10.0], "steel"),
  cube([6.2, 10.2, 6.05], [9.8, 12.6, 6.4], "glyph"),
  cube([6.2, 10.2, 9.6], [9.8, 12.6, 9.95], "glyph"),
  cube([6.05, 10.2, 6.2], [6.4, 12.6, 9.8], "glyph"),
  cube([9.6, 10.2, 6.2], [9.95, 12.6, 9.8], "glyph"),
  cube([7.3, 14.2, 7.3], [8.7, 15.4, 8.7], "steel"),
  cube([7.0, 15.4, 7.0], [9.0, 16.0, 9.0], "accent")
]);

write("race_gate", pedestal(T.blue, T.glow));
write("race_start", pedestal(T.green, T.green));
write("race_finish", pedestal(T.red, T.red));

const WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
  "mangrove", "cherry", "bamboo", "crimson", "warped"];
for (const w of WOODS) {
  write(`workbench_${w}`, {
    parent: "fpv:block/workbench",
    textures: {
      particle: `minecraft:block/${w}_planks`,
      wood: `minecraft:block/${w}_planks`,
      top: `minecraft:block/${w}_planks`
    }
  });
}
