import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { deflateSync } from "node:zlib";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const output = join(root, "src", "main", "resources", "assets", "fpv", "models", "item");
const textureOutput = join(root, "src", "main", "resources", "assets", "fpv", "textures", "item", "parts");
mkdirSync(output, { recursive: true });
mkdirSync(textureOutput, { recursive: true });
const generated = new Map();

const crcTable = Array.from({ length: 256 }, (_, n) => {
  let value = n;
  for (let bit = 0; bit < 8; bit++) value = value & 1 ? 0xedb88320 ^ (value >>> 1) : value >>> 1;
  return value >>> 0;
});

function crc32(bytes) {
  let crc = 0xffffffff;
  for (const byte of bytes) crc = crcTable[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function pngChunk(type, data) {
  const tag = Buffer.from(type);
  const length = Buffer.alloc(4);
  length.writeUInt32BE(data.length);
  const checksum = Buffer.alloc(4);
  checksum.writeUInt32BE(crc32(Buffer.concat([tag, data])));
  return Buffer.concat([length, tag, data, checksum]);
}

function writeRgbaPng(name, width, height, pixels) {
  const header = Buffer.alloc(13);
  header.writeUInt32BE(width, 0);
  header.writeUInt32BE(height, 4);
  header[8] = 8;
  header[9] = 6;
  const scanlines = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    const dst = y * (width * 4 + 1);
    scanlines[dst] = 0;
    pixels.copy(scanlines, dst + 1, y * width * 4, (y + 1) * width * 4);
  }
  const png = Buffer.concat([
    Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
    pngChunk("IHDR", header),
    pngChunk("IDAT", deflateSync(scanlines, { level: 9 })),
    pngChunk("IEND", Buffer.alloc(0))
  ]);
  writeFileSync(join(textureOutput, `${name}.png`), png);
}

function makePropTexture(name, blades, color) {
  const size = 64;
  const pixels = Buffer.alloc(size * size * 4);
  const put = (x, y, rgba) => {
    const index = (y * size + x) * 4;
    pixels[index] = rgba[0]; pixels[index + 1] = rgba[1]; pixels[index + 2] = rgba[2]; pixels[index + 3] = rgba[3];
  };
  const wrap = angle => Math.atan2(Math.sin(angle), Math.cos(angle));
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const dx = x + 0.5 - size / 2;
      const dy = y + 0.5 - size / 2;
      const radius = Math.hypot(dx, dy);
      let rgba = [0, 0, 0, 0];
      if (radius <= 5.2) {
        const shade = Math.round(185 - radius * 10);
        rgba = [shade, shade + 5, shade + 8, 255];
      } else if (radius <= 30.5) {
        const angle = Math.atan2(dy, dx);
        const t = (radius - 5.2) / 25.3;
        for (let blade = 0; blade < blades; blade++) {
          const rootAngle = blade * Math.PI * 2 / blades;
          const sweep = 0.12 + 0.48 * t;
          const lateral = Math.abs(wrap(angle - rootAngle - sweep)) * radius;
          const halfWidth = 1.35 + Math.sin(Math.PI * t) * 3.2 - t * 0.35;
          if (lateral <= halfWidth) {
            const edge = Math.min(1, Math.max(0, (halfWidth - lateral) / 1.2));
            const light = 0.82 + 0.18 * edge - 0.14 * t;
            rgba = [
              Math.round(color[0] * light),
              Math.round(color[1] * light),
              Math.round(color[2] * light),
              255
            ];
            break;
          }
        }
      }
      put(x, y, rgba);
    }
  }
  writeRgbaPng(name, size, size, pixels);
}

const directions = ["down", "up", "north", "south", "west", "east"];
const display = {
  gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
  ground: { translation: [0, 3, 0], scale: [0.35, 0.35, 0.35] },
  fixed: { rotation: [0, 180, 0], scale: [0.75, 0.75, 0.75] },
  thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.45, 0.45, 0.45] },
  firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 1.5, 0], scale: [0.55, 0.55, 0.55] }
};

function faces(texture, overrides = {}) {
  return Object.fromEntries(directions.map(direction => [direction, { texture: overrides[direction] ?? `#${texture}` }]));
}

function cube(from, to, texture, rotation, overrides) {
  const element = { from, to };
  if (rotation) element.rotation = rotation;
  element.faces = faces(texture, overrides);
  return element;
}

function rot(axis, angle, origin = [8, 8, 8], rescale = false) {
  return { origin, axis, angle, rescale };
}

function model(textures, elements, displayOverrides = {}) {
  return {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures,
    display: { ...display, ...displayOverrides },
    elements
  };
}

function write(name, value) {
  generated.set(name, value);
  writeFileSync(join(output, `${name}.json`), `${JSON.stringify(value, null, 2)}\n`);
}

function centered(width, y1, y2, length = width) {
  return [[8 - width / 2, y1, 8 - length / 2], [8 + width / 2, y2, 8 + length / 2]];
}

function frameTextures(name) {
  const paintedFrame = name === "frame_hexa_6inch" || name === "frame_octo_8inch"
    ? "frame_7inch"
    : name;
  return {
    frame: `fpv:item/${paintedFrame}`,
    carbon: "minecraft:block/black_concrete",
    edge: "minecraft:block/deepslate_tiles",
    metal: "minecraft:block/iron_block",
    accent: "minecraft:block/red_concrete"
  };
}

function ductRing(elements, x, y, z, radius, texture) {
  const thickness = radius < 2.3 ? 0.34 : 0.44;
  const corner = radius * 0.72;
  const low = y - 0.48;
  const high = y + 0.34;
  elements.push(cube([x - corner, low, z - radius], [x + corner, high, z - radius + thickness], texture));
  elements.push(cube([x - corner, low, z + radius - thickness], [x + corner, high, z + radius], texture));
  elements.push(cube([x - radius, low, z - corner], [x - radius + thickness, high, z + corner], texture));
  elements.push(cube([x + radius - thickness, low, z - corner], [x + radius, high, z + corner], texture));
  const diagonalLength = radius * 0.82;
  for (const [cx, cz, angle] of [
    [x - corner, z - corner, -45], [x + corner, z - corner, 45],
    [x - corner, z + corner, 45], [x + corner, z + corner, -45]
  ]) {
    elements.push(cube(
      [cx - thickness / 2, low, cz - diagonalLength / 2],
      [cx + thickness / 2, high, cz + diagonalLength / 2],
      texture,
      rot("y", angle, [cx, y, cz])
    ));
  }
}

function frameBase(name, mounts, layout, ductRadius = 0, cameraZ = 4.2) {
  const elements = [];
  const textures = frameTextures(name);
  const large = layout === "octo" || layout === "hexa";
  elements.push(cube(large ? [5.2, 7.25, 4.9] : [5.7, 7.35, 5.2], large ? [10.8, 8.15, 11.1] : [10.3, 8.15, 10.8], "carbon"));
  elements.push(cube(large ? [5.7, 8.15, 5.4] : [6.2, 8.15, 5.8], large ? [10.3, 8.55, 10.6] : [9.8, 8.55, 10.2], "frame"));

  if (layout === "quad") {
    const span = Math.max(...mounts.flatMap(([x, , z]) => [Math.abs(x - 8), Math.abs(z - 8)]));
    elements.push(cube([7.58, 7.42, 8 - span - 0.4], [8.42, 8.02, 8 + span + 0.4], "frame", rot("y", 45)));
    elements.push(cube([7.58, 7.42, 8 - span - 0.4], [8.42, 8.02, 8 + span + 0.4], "frame", rot("y", -45)));
  } else if (layout === "hexa") {
    elements.push(cube([0.8, 7.4, 7.58], [15.2, 8.04, 8.42], "frame"));
    elements.push(cube([7.58, 7.4, 0.8], [8.42, 8.04, 15.2], "frame", rot("y", 45)));
    elements.push(cube([7.58, 7.4, 0.8], [8.42, 8.04, 15.2], "frame", rot("y", -45)));
  } else {
    // True radial octocopter: four beams cross at the centre and end at eight evenly
    // spaced motor pads. The old four offset bars were the source of the broken H-shape.
    elements.push(cube([0.35, 7.35, 7.55], [15.65, 8.05, 8.45], "frame"));
    elements.push(cube([7.55, 7.35, 0.35], [8.45, 8.05, 15.65], "frame"));
    elements.push(cube([7.55, 7.35, 0.35], [8.45, 8.05, 15.65], "frame", rot("y", 45)));
    elements.push(cube([7.55, 7.35, 0.35], [8.45, 8.05, 15.65], "frame", rot("y", -45)));
  }

  for (const [x, y, z] of mounts) {
    elements.push(cube([x - 1.05, y - 0.45, z - 1.05], [x + 1.05, y, z + 1.05], "edge"));
    elements.push(cube([x - 0.7, y, z - 0.7], [x + 0.7, y + 0.25, z + 0.7], "metal"));
    if (ductRadius > 0) ductRing(elements, x, y, z, ductRadius, "frame");
  }

  for (const [x, z] of [[6.25, 6.0], [9.75, 6.0], [6.25, 10.0], [9.75, 10.0]]) {
    elements.push(cube([x - 0.18, 8.15, z - 0.18], [x + 0.18, 9.05, z + 0.18], "metal"));
  }
  elements.push(cube([6.1, 9.05, 5.8], [9.9, 9.35, 10.2], "carbon"));
  elements.push(cube([7.55, 9.35, 5.7], [8.45, 9.6, 10.3], "accent"));
  // Integrated FPV camera cage. The camera's rear pivot sits at cameraZ; the side
  // cheeks and roof make the mount visually continuous instead of floating in space.
  elements.push(cube([6.15, 7.8, cameraZ - 0.25], [6.65, 9.65, cameraZ + 2.1], "frame"));
  elements.push(cube([9.35, 7.8, cameraZ - 0.25], [9.85, 9.65, cameraZ + 2.1], "frame"));
  elements.push(cube([6.15, 9.4, cameraZ - 0.2], [9.85, 9.7, cameraZ + 1.1], "carbon"));
  return model(textures, elements, {
    gui: { rotation: [35, 225, 0], scale: [0.78, 0.78, 0.78] }
  });
}

const frameSpecs = {
  frame_5inch: { mounts: [[13.5, 8.5, 2.5], [2.5, 8.5, 2.5], [13.5, 8.5, 13.5], [2.5, 8.5, 13.5]], layout: "quad", cameraZ: 4.3 },
  frame_7inch: { mounts: [[15, 8.5, 1], [1, 8.5, 1], [14.2, 8.5, 14.6], [1.8, 8.5, 14.6]], layout: "quad", cameraZ: 3.5 },
  frame_tiny_whoop: { mounts: [[11.5, 8, 4.5], [4.5, 8, 4.5], [11.5, 8, 11.5], [4.5, 8, 11.5]], layout: "quad", duct: 2.05, cameraZ: 5.6 },
  frame_cinewhoop: { mounts: [[12.5, 8.5, 3.5], [3.5, 8.5, 3.5], [12.5, 8.5, 12.5], [3.5, 8.5, 12.5]], layout: "quad", duct: 2.65, cameraZ: 5.0 },
  frame_hexa_6inch: { mounts: [[13.7, 8.5, 2.5], [2.3, 8.5, 2.5], [15.2, 8.5, 8], [0.8, 8.5, 8], [13.7, 8.5, 13.5], [2.3, 8.5, 13.5]], layout: "hexa", cameraZ: 3.6 },
  frame_octo_8inch: { mounts: [[13.35, 8.5, 2.65], [2.65, 8.5, 2.65], [15.55, 8.5, 8], [0.45, 8.5, 8], [13.35, 8.5, 13.35], [2.65, 8.5, 13.35], [8, 8.5, 15.55], [8, 8.5, 0.45]], layout: "octo", cameraZ: 3.4 }
};
for (const [name, spec] of Object.entries(frameSpecs)) write(name, frameBase(name, spec.mounts, spec.layout, spec.duct, spec.cameraZ));

function motorModel(name, width, height) {
  const paintedMotor = name === "motor_2806_1300kv" || name === "motor_3115_900kv"
    ? "motor_2306_1700kv"
    : name;
  const main = `fpv:item/${paintedMotor}`;
  const [[x1], [x2]] = centered(width, 0, 0);
  // Attachment convention: y=8 is the motor-pad contact surface.
  const y1 = 8.0;
  const y2 = y1 + height;
  return model({ main, dark: "minecraft:block/black_concrete", metal: "minecraft:block/iron_block", copper: "minecraft:block/copper_block" }, [
    cube([x1 - 0.3, y1, x1 - 0.3], [x2 + 0.3, y1 + 0.28, x2 + 0.3], "dark"),
    cube([x1, y1 + 0.28, x1], [x2, y2, x2], "main"),
    cube([x1 + 0.18, y1 + 0.45, x1 - 0.08], [x2 - 0.18, y2 - 0.18, x1 + 0.15], "copper"),
    cube([x1 + 0.18, y1 + 0.45, x2 - 0.15], [x2 - 0.18, y2 - 0.18, x2 + 0.08], "copper"),
    cube([x1 + 0.15, y2 - 0.15, x1 + 0.15], [x2 - 0.15, y2 + 0.15, x2 - 0.15], "metal"),
    cube([7.78, y2 + 0.15, 7.78], [8.22, y2 + 0.75, 8.22], "metal")
  ]);
}

for (const [name, width, height] of [
  ["motor_1103_8000kv", 1.0, 1.15], ["motor_1404_4500kv", 1.3, 1.35],
  ["motor_2207_2450kv", 1.9, 2.0], ["motor_2306_1700kv", 2.0, 1.85],
  ["motor_2806_1300kv", 2.35, 1.9], ["motor_3115_900kv", 2.65, 2.8]
]) write(name, motorModel(name, width, height));

function propModel(name, diameter, blades) {
  const propColors = {
    prop_65mm: [224, 221, 186],
    prop_3x2x3: [208, 211, 191],
    prop_5x4x2: [188, 194, 168],
    prop_5x4x3: [198, 204, 177]
  };
  makePropTexture(name, blades, propColors[name]);
  const radius = diameter / 2;
  const disc = {
    from: [8 - radius, 8.02, 8 - radius],
    to: [8 + radius, 8.12, 8 + radius],
    faces: {
      down: { texture: "#blade", uv: [0, 0, 16, 16] },
      up: { texture: "#blade", uv: [0, 0, 16, 16] }
    }
  };
  const elements = [
    disc,
    cube([7.5, 7.82, 7.5], [8.5, 8.28, 8.5], "hub"),
    cube([7.78, 8.28, 7.78], [8.22, 8.52, 8.22], "nut")
  ];
  return model({ blade: `fpv:item/parts/${name}`, hub: "minecraft:block/iron_block", nut: "minecraft:block/black_concrete" }, elements, {
    gui: { rotation: [80, 225, 0], scale: [1.05, 1.05, 1.05] }
  });
}
write("prop_65mm", propModel("prop_65mm", 4.1, 4));
write("prop_3x2x3", propModel("prop_3x2x3", 5.5, 3));
write("prop_5x4x2", propModel("prop_5x4x2", 8.0, 2));
write("prop_5x4x3", propModel("prop_5x4x3", 8.0, 3));

function batteryModel(name, width, height, length, cells) {
  const [[x1, , z1], [x2, , z2]] = centered(width, 0, 0, length);
  // Attachment convention: battery bottom sits exactly on y=8.
  const y1 = 8.0;
  const y2 = y1 + height;
  const elements = [
    cube([x1, y1, z1], [x2, y2, z2], "pack"),
    cube([x1 - 0.08, y1 + height * 0.42, 7.55], [x2 + 0.08, y1 + height * 0.63, 8.45], "strap"),
    cube([x1 + 0.2, y2, z1 + 0.35], [x2 - 0.2, y2 + 0.15, z2 - 0.35], "label"),
    cube([x2, y1 + 0.25, 7.5], [x2 + 0.55, y1 + 0.75, 8.5], "plug")
  ];
  for (let i = 1; i < cells; i++) {
    const y = y1 + height * i / cells;
    elements.push(cube([x1 - 0.03, y - 0.04, z1 - 0.03], [x2 + 0.03, y + 0.04, z2 + 0.03], "edge"));
  }
  return model({ pack: `fpv:item/${name}`, strap: "minecraft:block/black_concrete", label: "minecraft:block/yellow_concrete", plug: "minecraft:block/red_concrete", edge: "minecraft:block/dark_oak_planks" }, elements);
}
write("battery_1s_300", batteryModel("battery_1s_300", 2.2, 1.5, 3.6, 1));
write("battery_4s_650", batteryModel("battery_4s_650", 3.0, 2.2, 5.0, 4));
write("battery_4s_1300", batteryModel("battery_4s_1300", 3.8, 2.8, 6.8, 4));
write("battery_6s_1100", batteryModel("battery_6s_1100", 4.0, 3.4, 6.2, 6));

function cameraModel(name, width, height, depth, wide) {
  const [[x1], [x2]] = centered(width, 0, 0);
  // The rear face at z=8 is the pivot captured by FrameSpecs.cameraMount().
  const y1 = 7.0;
  const z1 = 8 - depth;
  const z2 = 8.0;
  return model({ shell: `fpv:item/${name}`, dark: "minecraft:block/black_concrete", lens: "minecraft:block/blue_stained_glass", metal: "minecraft:block/iron_block", accent: wide ? "minecraft:block/orange_concrete" : "minecraft:block/lime_concrete" }, [
    cube([x1, y1, z1], [x2, y1 + height, z2], "shell", null, { north: "#dark" }),
    cube([7.2, y1 + 0.32, z1 - 0.38], [8.8, y1 + height - 0.28, z1], "dark"),
    cube([7.52, y1 + 0.58, z1 - 0.58], [8.48, y1 + height - 0.52, z1 - 0.36], "lens"),
    cube([x1 - 0.3, y1 + 0.18, 7.35], [x1, y1 + height - 0.18, 8.35], "metal"),
    cube([x2, y1 + 0.18, 7.35], [x2 + 0.3, y1 + height - 0.18, 8.35], "metal"),
    cube([x1 + 0.2, y1 + height, 7.25], [x2 - 0.2, y1 + height + 0.18, 8.15], "accent"),
    cube([x1 - 0.35, 7.55, 8.0], [x2 + 0.35, 8.25, 8.45], "dark")
  ]);
}
write("camera_standard", cameraModel("camera_standard", 2.4, 2.2, 2.2, false));
write("camera_wide", cameraModel("camera_wide", 3.2, 2.45, 2.4, true));

function boardModel(name, width, length, highPower = false, flightController = false) {
  const [[x1, , z1], [x2, , z2]] = centered(width, 0, 0, length);
  const elements = [
    cube([x1, 7.55, z1], [x2, 8.0, z2], "pcb"),
    cube([7.05, 8.0, 7.05], [8.35, 8.75, 8.35], "chip"),
    cube([8.7, 8.0, 7.25], [x2 - 0.25, 8.45, 8.0], "metal"),
    cube([x1 + 0.25, 8.0, 8.65], [x1 + 1.0, 8.55, z2 - 0.25], "copper")
  ];
  for (const [x, z] of [[x1 + 0.28, z1 + 0.28], [x2 - 0.28, z1 + 0.28], [x1 + 0.28, z2 - 0.28], [x2 - 0.28, z2 - 0.28]]) {
    elements.push(cube([x - 0.13, 7.45, z - 0.13], [x + 0.13, 8.12, z + 0.13], "metal"));
  }
  if (highPower) {
    elements.push(cube([6.8, 8.75, 6.9], [9.2, 9.35, 9.1], "sink"));
    for (let x = 7.0; x <= 9.0; x += 0.5) elements.push(cube([x, 9.35, 7.0], [x + 0.18, 9.7, 9.0], "metal"));
  }
  if (!flightController) {
    elements.push(cube([x2 - 0.25, 8.05, 8.25], [x2 + 0.2, 8.5, 8.7], "antenna"));
    elements.push(cube([x2 + 0.05, 8.28, 8.42], [x2 + 2.0, 8.48, 8.62], "antenna", rot("z", -22.5, [x2 + 0.05, 8.38, 8.52])));
  }
  return model({ pcb: `fpv:item/${name}`, chip: "minecraft:block/black_concrete", metal: "minecraft:block/iron_block", copper: "minecraft:block/copper_block", sink: "minecraft:block/cut_copper", antenna: "minecraft:block/red_concrete" }, elements);
}
write("fc_f411", boardModel("fc_f411", 4.6, 4.6, false, true));
write("fc_h7", boardModel("fc_h7", 5.4, 5.4, true, true));
write("vtx_25mw", boardModel("vtx_25mw", 3.5, 3.0, false, false));
write("vtx_200mw", boardModel("vtx_200mw", 4.2, 3.6, false, false));
write("vtx_800mw", boardModel("vtx_800mw", 5.0, 4.2, true, false));

const moduleTextures = {
  carbon: "minecraft:block/black_concrete",
  metal: "minecraft:block/iron_block",
  dark_metal: "minecraft:block/deepslate_tiles",
  orange: "minecraft:block/orange_concrete",
  red: "minecraft:block/red_concrete",
  olive: "minecraft:block/green_concrete",
  brown: "minecraft:block/brown_concrete",
  lens: "minecraft:block/light_blue_stained_glass",
  yellow: "minecraft:block/yellow_concrete"
};

write("module_payload_bay", model(moduleTextures, [
  cube([4.5, 7.45, 4.2], [11.5, 8.0, 11.8], "carbon"),
  cube([4.7, 5.85, 4.5], [5.45, 7.45, 11.5], "metal"),
  cube([10.55, 5.85, 4.5], [11.3, 7.45, 11.5], "metal"),
  cube([5.45, 5.9, 5.0], [10.55, 6.3, 5.7], "dark_metal"),
  cube([5.45, 5.9, 10.3], [10.55, 6.3, 11.0], "dark_metal"),
  cube([6.0, 5.25, 6.0], [7.2, 5.9, 10.0], "red"),
  cube([8.8, 5.25, 6.0], [10.0, 5.9, 10.0], "red"),
  cube([7.45, 7.72, 3.95], [8.55, 8.0, 5.0], "orange")
]));

write("module_warhead", model(moduleTextures, [
  cube([6.7, 5.1, 4.0], [9.3, 7.25, 7.0], "olive"),
  cube([7.05, 5.4, 2.8], [8.95, 6.95, 4.0], "yellow"),
  cube([7.45, 5.7, 2.1], [8.55, 6.65, 2.8], "metal"),
  cube([7.1, 5.45, 7.0], [8.9, 6.85, 12.7], "dark_metal"),
  cube([6.2, 4.65, 10.8], [7.25, 7.65, 12.4], "olive"),
  cube([8.75, 4.65, 10.8], [9.8, 7.65, 12.4], "olive"),
  cube([7.25, 4.35, 10.8], [8.75, 5.45, 12.4], "olive"),
  cube([7.25, 6.85, 10.8], [8.75, 7.95, 12.4], "olive"),
  cube([6.3, 7.55, 6.0], [9.7, 8.0, 10.0], "carbon"),
  cube([7.6, 7.25, 4.6], [8.4, 7.6, 6.5], "red")
]));

write("module_dart_gun", model(moduleTextures, [
  cube([5.7, 5.8, 6.2], [10.3, 7.5, 10.5], "dark_metal"),
  cube([6.25, 6.15, 4.2], [9.75, 7.25, 6.2], "carbon"),
  cube([7.3, 6.3, 1.8], [8.7, 7.05, 4.2], "metal"),
  cube([7.62, 6.45, 0.8], [8.38, 6.9, 1.8], "dark_metal"),
  cube([5.2, 5.1, 8.0], [7.0, 5.8, 10.0], "brown"),
  cube([9.0, 5.1, 8.0], [10.8, 5.8, 10.0], "brown"),
  cube([9.55, 6.45, 5.3], [10.55, 7.15, 6.1], "lens"),
  cube([5.4, 7.5, 7.0], [10.6, 8.0, 8.0], "orange")
]));

write("module_storage", model(moduleTextures, [
  cube([4.4, 4.0, 4.6], [11.6, 7.5, 11.4], "brown"),
  cube([4.15, 7.5, 4.35], [11.85, 8.0, 11.65], "dark_metal"),
  cube([4.15, 3.75, 4.35], [11.85, 4.0, 11.65], "metal"),
  cube([5.1, 3.7, 4.2], [5.75, 8.0, 11.8], "carbon"),
  cube([10.25, 3.7, 4.2], [10.9, 8.0, 11.8], "carbon"),
  cube([7.1, 5.0, 4.15], [8.9, 6.1, 4.6], "metal"),
  cube([7.55, 5.3, 3.9], [8.45, 5.8, 4.15], "orange")
]));

function planeModel(kind) {
  const scout = kind === "plane_scout";
  const kamikaze = kind === "plane_kamikaze";
  const wingHalf = scout ? 7.5 : kamikaze ? 5.6 : 6.5;
  const bodyWidth = scout ? 1.25 : 1.55;
  const elements = [
    cube([8 - bodyWidth / 2, 7.2, 2.0], [8 + bodyWidth / 2, 8.8, 13.8], "body"),
    cube([8 - wingHalf, 7.45, 6.1], [8 + wingHalf, 8.05, 9.0], "wing"),
    cube([8 - wingHalf * 0.72, 7.45, 8.5], [8 + wingHalf * 0.72, 8.0, 10.0], "wing", rot("y", -22.5)),
    cube([7.45, 8.75, 10.9], [8.55, 11.3, 13.4], "tail"),
    cube([5.7, 7.45, 11.8], [10.3, 8.05, 13.5], "tail"),
    cube([7.25, 7.35, 1.25], [8.75, 8.65, 2.0], kamikaze ? "warhead" : "dark"),
    cube([7.72, 7.55, 0.5], [8.28, 8.45, 1.25], kamikaze ? "warhead" : "metal"),
    cube([5.8, 7.75, 0.75], [10.2, 8.0, 1.0], "prop", rot("z", 22.5, [8, 7.9, 0.9]))
  ];
  if (scout) {
    elements.push(cube([7.1, 6.55, 4.2], [8.9, 7.2, 5.8], "dark"));
    elements.push(cube([7.55, 6.2, 3.8], [8.45, 6.8, 4.4], "lens"));
  }
  if (kamikaze) {
    elements.push(cube([6.4, 6.65, 4.6], [9.6, 7.2, 8.6], "warhead"));
    elements.push(cube([7.55, 6.35, 5.1], [8.45, 6.65, 8.1], "yellow"));
  }
  return model({
    body: "fpv:item/plane",
    wing: scout ? "minecraft:block/light_gray_concrete" : kamikaze ? "minecraft:block/green_concrete" : "minecraft:block/white_concrete",
    tail: scout ? "minecraft:block/light_blue_concrete" : "minecraft:block/red_concrete",
    dark: "minecraft:block/black_concrete",
    metal: "minecraft:block/iron_block",
    prop: "fpv:item/prop_5x4x2",
    lens: "minecraft:block/light_blue_stained_glass",
    warhead: "minecraft:block/green_concrete",
    yellow: "minecraft:block/yellow_concrete"
  }, elements, {
    gui: { rotation: [35, 225, 0], scale: [0.78, 0.78, 0.78] }
  });
}

write("plane", planeModel("plane"));
write("plane_scout", planeModel("plane_scout"));
write("plane_kamikaze", planeModel("plane_kamikaze"));

write("drop_charge", model({
  shell: "minecraft:block/red_concrete",
  dark: "minecraft:block/black_concrete",
  metal: "minecraft:block/iron_block",
  fuse: "minecraft:block/yellow_concrete"
}, [
  cube([6.1, 5.2, 6.1], [9.9, 10.8, 9.9], "shell"),
  cube([5.8, 6.0, 5.8], [10.2, 6.55, 10.2], "dark"),
  cube([5.8, 9.45, 5.8], [10.2, 10.0, 10.2], "dark"),
  cube([7.0, 10.8, 7.0], [9.0, 11.45, 9.0], "metal"),
  cube([7.65, 11.45, 7.65], [8.35, 13.2, 8.35], "fuse", rot("z", -22.5, [8, 11.45, 8]))
]));

function round(value) {
  return Math.round(value * 1000) / 1000;
}

function transformedPart(name, offset, scale, prefix, groupRotation) {
  const source = generated.get(name);
  if (!source) throw new Error(`Unknown generated part: ${name}`);
  const textures = Object.fromEntries(Object.entries(source.textures).map(([key, value]) => [`${prefix}_${key}`, value]));
  const point = values => values.map((value, axis) => round(8 + (value - 8) * scale + offset[axis]));
  const elements = source.elements.map(element => {
    const copy = { from: point(element.from), to: point(element.to) };
    if (element.rotation) copy.rotation = { ...element.rotation, origin: point(element.rotation.origin) };
    else if (groupRotation) copy.rotation = { ...groupRotation, origin: point(groupRotation.origin ?? [8, 8, 8]) };
    copy.faces = Object.fromEntries(Object.entries(element.faces).map(([face, data]) => [
      face,
      { ...data, texture: data.texture.startsWith("#") ? `#${prefix}_${data.texture.slice(1)}` : data.texture }
    ]));
    return copy;
  });
  return { textures, elements };
}

function assembledModel(parts, extraTextures = {}, extraElements = []) {
  const textures = { ...extraTextures };
  const elements = [...extraElements];
  parts.forEach((part, index) => {
    const transformed = transformedPart(part.name, part.offset ?? [0, 0, 0], part.scale ?? 1, `p${index}`, part.rotation);
    Object.assign(textures, transformed.textures);
    elements.push(...transformed.elements);
  });
  return model(textures, elements, {
    gui: { rotation: [32, 225, 0], translation: [0, -0.4, 0], scale: [0.68, 0.68, 0.68] },
    ground: { translation: [0, 2.5, 0], scale: [0.3, 0.3, 0.3] },
    thirdperson_righthand: { rotation: [70, 45, 0], translation: [0, 2, 0], scale: [0.35, 0.35, 0.35] }
  });
}

const quadMounts = frameSpecs.frame_5inch.mounts;
function quadParts(moduleName = "module_payload_bay") {
  const parts = [
    { name: "frame_5inch" },
    { name: "fc_h7", offset: [0, 0.75, 0], scale: 0.48 },
    { name: "vtx_800mw", offset: [0, 0.55, 2.0], scale: 0.42 },
    { name: "battery_4s_1300", offset: [0, 1.6, 0.8], scale: 0.68 },
    { name: "camera_standard", offset: [0, 0.65, -3.7], scale: 0.72, rotation: rot("x", 22.5) },
    { name: moduleName, offset: [0, -0.75, 0], scale: 0.78 }
  ];
  for (const [x, y, z] of quadMounts) {
    const offset = [x - 8, y - 8, z - 8];
    parts.push({ name: "motor_2207_2450kv", offset, scale: 0.72 });
    parts.push({ name: "prop_5x4x3", offset: [offset[0], offset[1] + 2.0, offset[2]], scale: 0.76 });
  }
  return parts;
}

write("assembled_drone_quad", assembledModel(quadParts()));
write("assembled_drone_kamikaze", assembledModel(quadParts("module_warhead")));

const remoteAntennae = [
  cube([10.25, 9.1, 9.5], [10.65, 14.0, 9.9], "antenna", rot("z", -22.5, [10.45, 9.2, 9.7])),
  cube([5.35, 9.1, 9.5], [5.75, 13.1, 9.9], "antenna", rot("z", 22.5, [5.55, 9.2, 9.7]))
];
write("assembled_drone_remote", assembledModel(quadParts(), { antenna: "minecraft:block/black_concrete" }, remoteAntennae));

function whoopParts(cine = false) {
  const spec = cine ? frameSpecs.frame_cinewhoop : frameSpecs.frame_tiny_whoop;
  const parts = [
    { name: cine ? "frame_cinewhoop" : "frame_tiny_whoop" },
    { name: "fc_f411", offset: [0, 0.55, 0.5], scale: cine ? 0.5 : 0.38 },
    { name: cine ? "vtx_200mw" : "vtx_25mw", offset: [0, 0.45, 1.6], scale: cine ? 0.46 : 0.34 },
    { name: cine ? "battery_4s_650" : "battery_1s_300", offset: [0, cine ? 1.55 : 1.35, 0.6], scale: cine ? 0.62 : 0.5 },
    {
      name: cine ? "camera_wide" : "camera_standard",
      offset: [0, cine ? 0.7 : 0.4, cine ? -3.0 : -2.4],
      scale: cine ? 0.62 : 0.48,
      rotation: rot("x", 22.5)
    }
  ];
  for (const [x, y, z] of spec.mounts) {
    const offset = [x - 8, y - 8, z - 8];
    parts.push({ name: cine ? "motor_1404_4500kv" : "motor_1103_8000kv", offset, scale: cine ? 0.64 : 0.5 });
    parts.push({
      name: cine ? "prop_3x2x3" : "prop_65mm",
      offset: [offset[0], offset[1] + (cine ? 1.4 : 1.05), offset[2]],
      scale: cine ? 0.68 : 0.56
    });
  }
  return parts;
}

write("assembled_drone_whoop", assembledModel(whoopParts(false)));
write("assembled_drone_cinewhoop", assembledModel(whoopParts(true)));

// Stock and NBT-driven drone items now have a readable 3D preview instead of a flat generated sprite.
write("assembled_drone", { parent: "fpv:item/assembled_drone_quad" });
write("modular_drone", { parent: "fpv:item/assembled_drone_quad" });
write("racing_drone", { parent: "fpv:item/assembled_drone_quad" });
write("remote_racing_drone", { parent: "fpv:item/assembled_drone_remote" });
write("kamikaze_drone", { parent: "fpv:item/assembled_drone_kamikaze" });
write("tiny_whoop", { parent: "fpv:item/assembled_drone_whoop" });
write("cinewhoop", { parent: "fpv:item/assembled_drone_cinewhoop" });

console.log(`Generated FPV item models in ${output}`);
