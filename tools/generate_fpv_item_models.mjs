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
  const wrap = angle => Math.atan2(Math.sin(angle), Math.cos(angle));
  const hubRadius = 4.6;
  const tipRadius = 30.2;

  const shade = (px, py) => {
    const dx = px - size / 2;
    const dy = py - size / 2;
    const radius = Math.hypot(dx, dy);
    if (radius <= hubRadius) {
      let level = 178 - radius * 3.4;
      if (Math.abs(radius - 3.1) < 0.55) level -= 40;
      if (radius < 1.35) level -= 52;
      return [level, level + 4, level + 10, 255];
    }
    if (radius > tipRadius) return null;
    const angle = Math.atan2(dy, dx);
    const t = (radius - hubRadius) / (tipRadius - hubRadius);
    for (let blade = 0; blade < blades; blade++) {
      const rootAngle = blade * Math.PI * 2 / blades;
      const rake = 0.06 + 0.38 * t;
      const lateral = wrap(angle - rootAngle - rake) * radius;
      const chord = (0.7 + 4.5 * Math.sin(Math.PI * Math.pow(t, 0.9))) * (1 - 0.16 * t) + 0.2;
      const halfChord = chord * Math.sqrt(Math.max(0, 1 - Math.pow(t, 3.2)));
      if (halfChord <= 0 || Math.abs(lateral) > halfChord) continue;
      const across = lateral / halfChord;
      let light = 0.68 + 0.34 * Math.sqrt(Math.max(0, 1 - across * across)) + 0.16 * across - 0.14 * t;
      if (across > 0.72) light += 0.14;
      if (across < -0.78) light -= 0.16;
      if (t < 0.13) light -= 0.12;
      light = Math.min(1.1, Math.max(0.4, light));
      return [color[0] * light, color[1] * light, color[2] * light, 255];
    }
    return null;
  };

  const clamp = v => Math.max(0, Math.min(255, Math.round(v)));
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0, g = 0, b = 0, hits = 0;
      for (let sy = 0; sy < 4; sy++) {
        for (let sx = 0; sx < 4; sx++) {
          const rgba = shade(x + (sx + 0.5) / 4, y + (sy + 0.5) / 4);
          if (!rgba) continue;
          r += rgba[0]; g += rgba[1]; b += rgba[2];
          hits++;
        }
      }
      const index = (y * size + x) * 4;
      if (!hits) continue;
      pixels[index] = clamp(r / hits);
      pixels[index + 1] = clamp(g / hits);
      pixels[index + 2] = clamp(b / hits);
      pixels[index + 3] = clamp(255 * hits / 16);
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

const FACE_UV = {
  down: (f, t) => [f[0], 16 - t[2], t[0], 16 - f[2]],
  up: (f, t) => [f[0], f[2], t[0], t[2]],
  north: (f, t) => [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]],
  south: (f, t) => [f[0], 16 - t[1], t[0], 16 - f[1]],
  west: (f, t) => [f[2], 16 - t[1], t[2], 16 - f[1]],
  east: (f, t) => [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]
};

// Vanilla derives a face UV straight from the element box, so a part reaching past the
// 0-16 space samples outside its own sprite and renders as a hole. Pinning those faces
// back onto the sprite is what keeps long arms and outboard motor pads opaque.
function clampFaceUvs(elements) {
  for (const element of elements ?? []) {
    for (const [direction, face] of Object.entries(element.faces ?? {})) {
      if (face.uv) continue;
      const uv = FACE_UV[direction](element.from, element.to);
      if (uv.every(v => v >= -0.001 && v <= 16.001)) continue;
      const fit = ([a, b]) => {
        const span = Math.min(Math.abs(b - a), 16);
        const lo = Math.min(Math.max(Math.min(a, b), 0), 16 - span);
        return a <= b ? [lo, lo + span] : [lo + span, lo];
      };
      const [u1, u2] = fit([uv[0], uv[2]]);
      const [v1, v2] = fit([uv[1], uv[3]]);
      face.uv = [u1, v1, u2, v2].map(v => Number(v.toFixed(4)));
    }
  }
}

function write(name, value) {
  clampFaceUvs(value.elements);
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
    accent: "minecraft:block/red_concrete",
    strap: "minecraft:block/black_wool",
    grip: "minecraft:block/gray_wool",
    rubber: "minecraft:block/black_wool",
    plug: "minecraft:block/yellow_concrete"
  };
}

// A short post drawn as two cubes crossed at 45 deg: the eight-sided silhouette reads as
// round at item scale, where a single box reads as a square peg.
function roundPost(elements, x, z, y1, y2, half, texture) {
  elements.push(cube([x - half, y1, z - half], [x + half, y2, z + half], texture));
  elements.push(cube([x - half, y1, z - half], [x + half, y2, z + half], texture, rot("y", 45, [x, (y1 + y2) / 2, z])));
}

// A prop duct built as a regular octagon: four axis-aligned walls plus four at 45 deg,
// every midpoint the same distance from the hub. Sizing the sides as 2*r*tan(22.5) is what
// stops it reading as a notched square.
function ductRing(elements, x, y, z, radius, texture, lipTexture) {
  const thickness = radius < 2.3 ? 0.3 : 0.36;
  const half = radius * 0.4142;
  const t = thickness / 2;
  const deep = radius >= 2.3;
  const low = y - (deep ? 0.5 : 0.34);
  const high = y + radius * (deep ? 0.55 : 0.42);
  const mid = (low + high) / 2;
  const lip = 0.16;

  const wall = (from, to, angle, origin) => {
    elements.push(cube(from, to, texture, angle ? rot("y", angle, origin) : null));
    elements.push(cube(
      [from[0] - lip, high, from[2] - lip],
      [to[0] + lip, high + 0.16, to[2] + lip],
      lipTexture,
      angle ? rot("y", angle, [origin[0], high, origin[2]]) : null
    ));
  };

  wall([x - half, low, z - radius - t], [x + half, high, z - radius + t]);
  wall([x - half, low, z + radius - t], [x + half, high, z + radius + t]);
  wall([x - radius - t, low, z - half], [x - radius + t, high, z + half]);
  wall([x + radius - t, low, z - half], [x + radius + t, high, z + half]);

  const d = radius * 0.7071;
  for (const [ox, oz, angle] of [[-1, -1, 45], [1, -1, -45], [-1, 1, -45], [1, 1, 45]]) {
    const cx = x + ox * d;
    const cz = z + oz * d;
    wall([cx - half, low, cz - t], [cx + half, high, cz + t], angle, [cx, mid, cz]);
  }
}

function armReach(mount) {
  return Math.hypot(mount[0] - 8, mount[2] - 8);
}

/**
 * One frame arm, drawn as a bar along Z and rotated into place. Both ends of a single
 * diagonal bar are real motor pads, so the two halves get their own lengths — that is
 * what stops the arms falling short of the pads on frames whose mounts are not square.
 */
function armBar(elements, angle, front, rear, y1, y2, half, texture) {
  // Vanilla only accepts +-45/+-22.5 element rotations, so a quarter turn is baked into the box.
  if (angle === 90 || angle === -90) {
    const f = angle === 90 ? front : rear;
    const r = angle === 90 ? rear : front;
    elements.push(cube([8 - f, y1, 8 - half], [8 + r, y2, 8 + half], texture));
    return;
  }
  elements.push(cube([8 - half, y1, 8 - front], [8 + half, y2, 8 + rear], texture,
    angle === 0 ? null : rot("y", angle)));
}

/**
 * Skid gear for the heavy lifters. Two rails are sandwiched into the centre plate and
 * carry every strut, so the legs grow out of the body instead of hanging in mid air;
 * the struts are 45 deg elements, the only diagonal vanilla accepts.
 */
function landingGear(elements, by1, bz1, bz2) {
  const railY2 = by1 + 0.2;
  const railY1 = by1 - 0.2;
  const railZ1 = bz1 + 0.75;
  const railZ2 = bz2 - 0.75;
  const legZ = [railZ1 + 0.85, railZ2 - 0.85];
  const hingeY = railY2 - 0.12;
  const reachOut = 3.0;
  const drop = reachOut * Math.SQRT2;
  const footY = hingeY - reachOut;
  const skidY1 = footY - 0.62;
  const skidY2 = footY + 0.34;
  const skidZ1 = bz1 - 0.1;
  const skidZ2 = bz2 + 0.1;

  for (const side of [-1, 1]) {
    const railX = 8 + side * 1.1;
    const skidX = 8 + side * (reachOut + 1.1);

    elements.push(cube([railX - 0.55, railY1, railZ1], [railX + 0.55, railY2, railZ2], "frame"));
    elements.push(cube([railX - 0.42, railY1 - 0.14, railZ1 + 0.35], [railX + 0.42, railY1, railZ2 - 0.35], "carbon"));

    for (const z of legZ) {
      const pivot = [railX, hingeY, z];
      elements.push(cube([railX - 0.46, railY1 - 0.06, z - 0.46], [railX + 0.46, railY2 + 0.24, z + 0.46], "edge"));
      elements.push(cube([railX - 0.34, hingeY - drop, z - 0.34],
        [railX + 0.34, hingeY + 0.3, z + 0.34], "metal", rot("z", side * 45, pivot)));
      elements.push(cube([railX - 0.42, hingeY - drop + 0.5, z - 0.2],
        [railX + 0.42, hingeY - drop + 1.5, z + 0.2], "edge", rot("z", side * 45, pivot)));
      elements.push(cube([skidX - 0.46, footY - 0.2, z - 0.46], [skidX + 0.46, footY + 0.62, z + 0.46], "edge"));
    }

    elements.push(cube([skidX - 0.6, footY - 0.28, skidZ1], [skidX + 0.6, skidY2, skidZ2], "metal"));
    elements.push(cube([skidX - 0.66, skidY1, skidZ1 + 0.15], [skidX + 0.66, footY - 0.28, skidZ2 - 0.15], "rubber"));

    // Upturned ends, so the skids read as tubes bent up at the tips rather than cut bars.
    const noseO = [skidX, footY, skidZ1 + 0.3];
    const tailO = [skidX, footY, skidZ2 - 0.3];
    elements.push(cube([skidX - 0.5, skidY1 + 0.2, skidZ1 - 1.9], [skidX + 0.5, skidY2 - 0.1, skidZ1 + 0.3],
      "metal", rot("x", 22.5, noseO)));
    elements.push(cube([skidX - 0.5, skidY1 + 0.2, skidZ2 - 0.3], [skidX + 0.5, skidY2 - 0.1, skidZ2 + 1.9],
      "metal", rot("x", -22.5, tailO)));
  }
}

function frameBase(name, mounts, layout, ductRadius = 0, cameraZ = 4.2) {
  const elements = [];
  const textures = frameTextures(name);
  const large = layout === "octo" || layout === "hexa";
  const whoop = ductRadius > 0;
  const reach = mounts.map(armReach);

  const bx1 = large ? 5.95 : 5.7;
  const bx2 = large ? 10.05 : 10.3;
  const bz1 = large ? 4.55 : 5.2;
  const bz2 = large ? 11.45 : 10.8;
  const by1 = large ? 7.22 : 7.3;

  // Lower stack: bottom plate, side rails and bulkheads instead of one solid lump, so the
  // frame reads as the plate sandwich a real quad is built from.
  elements.push(cube([bx1, by1, bz1], [bx2, by1 + 0.34, bz2], "carbon"));
  elements.push(cube([bx1 + 0.2, by1 + 0.34, bz1 + 0.35], [bx1 + 0.72, 8.14, bz2 - 0.35], "edge"));
  elements.push(cube([bx2 - 0.72, by1 + 0.34, bz1 + 0.35], [bx2 - 0.2, 8.14, bz2 - 0.35], "edge"));
  elements.push(cube([bx1 + 0.72, by1 + 0.34, bz1 + 0.3], [bx2 - 0.72, 8.14, bz1 + 0.82], "carbon"));
  elements.push(cube([bx1 + 0.72, by1 + 0.34, bz2 - 0.82], [bx2 - 0.72, 8.14, bz2 - 0.3], "carbon"));
  elements.push(cube(large ? [6.4, 8.14, 5.1] : [6.2, 8.14, 5.8], large ? [9.6, 8.55, 10.9] : [9.8, 8.55, 10.2], "frame"));

  const armY1 = 7.42;
  const armY2 = 8.06;
  const armHalf = large ? 0.44 : 0.46;
  const capHalf = armHalf - 0.15;
  const overhang = 0.55;

  if (layout === "quad") {
    // mounts are [front-right, front-left, rear-right, rear-left]; one bar links FL-RR,
    // the other FR-RL, so each bar's two halves are sized from its own two pads.
    const bars = [[45, reach[1], reach[2]], [-45, reach[0], reach[3]]];
    for (const [angle, front, rear] of bars) {
      armBar(elements, angle, front + overhang, rear + overhang, armY1, armY2, armHalf, "frame");
      armBar(elements, angle, front + overhang - 0.3, rear + overhang - 0.3, armY2, armY2 + 0.17, capHalf, "carbon");
    }
    elements.push(cube([6.15, armY1 - 0.14, 6.15], [9.85, armY1, 9.85], "carbon", rot("y", 45)));
  } else if (layout === "hexa") {
    const side = Math.max(reach[2], reach[3]);
    armBar(elements, 90, side + overhang, side + overhang, armY1, armY2, armHalf, "frame");
    const bars = [[45, reach[1], reach[4]], [-45, reach[0], reach[5]]];
    for (const [angle, front, rear] of bars) {
      armBar(elements, angle, front + overhang, rear + overhang, armY1, armY2, armHalf, "frame");
    }
    armBar(elements, 90, side + overhang - 0.3, side + overhang - 0.3, armY2, armY2 + 0.17, capHalf, "carbon");
    for (const [angle, front, rear] of bars) {
      armBar(elements, angle, front + overhang - 0.3, rear + overhang - 0.3, armY2, armY2 + 0.17, capHalf, "carbon");
    }
    elements.push(cube([6.15, armY1 - 0.14, 6.3], [9.85, armY1, 9.7], "carbon"));
  } else {
    const axis = Math.max(reach[2], reach[3], reach[6], reach[7]);
    const diag = Math.max(reach[0], reach[1], reach[4], reach[5]);
    for (const [angle, len] of [[90, axis], [0, axis], [45, diag], [-45, diag]]) {
      armBar(elements, angle, len + overhang, len + overhang, armY1, armY2, armHalf, "frame");
      armBar(elements, angle, len + overhang - 0.3, len + overhang - 0.3, armY2, armY2 + 0.17, capHalf, "carbon");
    }
    elements.push(cube([6.15, armY1 - 0.14, 6.15], [9.85, armY1, 9.85], "carbon"));
    elements.push(cube([6.15, armY1 - 0.14, 6.15], [9.85, armY1, 9.85], "carbon", rot("y", 45)));
  }

  // Each arm tip flares into a round pad that reaches the motor base, so nothing
  // shows through between the bar and the motor sitting on it.
  const padHalf = whoop ? 0.86 : large ? 1.1 : 1.16;
  for (const [mx, my, mz] of mounts) {
    const padTop = Math.max(my, armY2);
    // A thin plate carries the arm, and only a narrow boss climbs to the motor base, so
    // the pad reads as a mount rather than a block.
    const plateTop = Math.min(padTop, armY2 + 0.14);
    const bossHalf = padHalf - 0.34;
    const plateMid = (armY1 + plateTop) / 2;
    elements.push(cube([mx - padHalf, armY1 - 0.02, mz - padHalf],
      [mx + padHalf, plateTop, mz + padHalf], "frame"));
    elements.push(cube([mx - padHalf, armY1 - 0.015, mz - padHalf],
      [mx + padHalf, plateTop - 0.005, mz + padHalf], "frame", rot("y", 45, [mx, plateMid, mz])));
    if (padTop > plateTop) {
      const bossMid = (plateTop + padTop) / 2;
      elements.push(cube([mx - bossHalf, plateTop, mz - bossHalf],
        [mx + bossHalf, padTop, mz + bossHalf], "frame"));
      elements.push(cube([mx - bossHalf, plateTop + 0.005, mz - bossHalf],
        [mx + bossHalf, padTop - 0.005, mz + bossHalf], "frame", rot("y", 45, [mx, bossMid, mz])));
    }
    const ring = padHalf - 0.26;
    elements.push(cube([mx - ring, armY1 - 0.14, mz - ring],
      [mx + ring, armY1 - 0.02, mz + ring], "carbon"));
    elements.push(cube([mx - ring, armY1 - 0.135, mz - ring],
      [mx + ring, armY1 - 0.025, mz + ring], "carbon", rot("y", 45, [mx, armY1 - 0.08, mz])));
    if (!whoop) {
      for (const [ox, oz] of [[-1, -1], [1, -1], [-1, 1], [1, 1]]) {
        const bx = mx + ox * (padHalf - 0.36);
        const bz = mz + oz * (padHalf - 0.36);
        elements.push(cube([bx - 0.15, padTop - 0.02, bz - 0.15],
          [bx + 0.15, padTop + 0.09, bz + 0.15], "metal"));
      }
    }
  }

  if (ductRadius > 0) {
    for (const [x, y, z] of mounts) ductRing(elements, x, y, z, ductRadius, "frame", "carbon");

    // Webbing between neighbouring ducts: without it the whoop frame is see-through
    // between each ring and the centre pod.
    const xs = [...new Set(mounts.map(m => m[0]))].sort((a, b) => a - b);
    const zs = [...new Set(mounts.map(m => m[2]))].sort((a, b) => a - b);
    const low = mounts[0][1] - (ductRadius >= 2.3 ? 0.5 : 0.34);
    const wy1 = low - 0.15;
    const wy2 = low + 0.75;
    const hw = ductRadius >= 2.3 ? 0.9 : 0.72;
    const bite = 0.35;
    for (const z of zs) {
      elements.push(cube([xs[0] + ductRadius - bite, wy1, z - hw],
        [xs[1] - ductRadius + bite, wy2, z + hw], "frame"));
      elements.push(cube([xs[0] + ductRadius - bite, wy2, z - hw + 0.18],
        [xs[1] - ductRadius + bite, wy2 + 0.16, z + hw - 0.18], "carbon"));
    }
    for (const x of xs) {
      elements.push(cube([x - hw, wy1, zs[0] + ductRadius - bite],
        [x + hw, wy2, zs[1] - ductRadius + bite], "frame"));
      elements.push(cube([x - hw + 0.18, wy2, zs[0] + ductRadius - bite],
        [x + hw - 0.18, wy2 + 0.16, zs[1] - ductRadius + bite], "carbon"));
    }
    for (const [mx, , mz] of mounts) {
      const dx = mx < 8 ? 1 : -1;
      const dz = mz < 8 ? 1 : -1;
      const cx = mx + dx * (ductRadius * 0.72);
      const cz = mz + dz * (ductRadius * 0.72);
      const ex = 8 - cx;
      const ez = 8 - cz;
      elements.push(cube([Math.min(cx, cx + ex), wy1, Math.min(cz, cz + ez)],
        [Math.max(cx, cx + ex), wy1 + 0.5, Math.max(cz, cz + ez)], "frame"));
    }
  }

  for (const [x, z] of [[6.25, 6.0], [9.75, 6.0], [6.25, 10.0], [9.75, 10.0]]) {
    roundPost(elements, x, z, 8.55, 9.05, 0.2, "metal");
    elements.push(cube([x - 0.32, 8.98, z - 0.32], [x + 0.32, 9.07, z + 0.32], "edge"));
  }

  elements.push(cube([6.1, 9.05, 5.8], [9.9, 9.35, 10.2], "carbon"));
  elements.push(cube([6.45, 9.35, 6.0], [9.55, 9.47, 6.6], "rubber"));
  elements.push(cube([6.45, 9.35, 9.4], [9.55, 9.47, 10.0], "rubber"));
  elements.push(cube([7.2, 9.35, 5.95], [8.8, 9.46, 10.05], "grip"));
  elements.push(cube([6.45, 9.35, 6.45], [9.55, 9.55, 6.95], "strap"));
  elements.push(cube([6.45, 9.35, 9.05], [9.55, 9.55, 9.55], "strap"));

  // Integrated FPV camera cage. The camera's rear pivot sits at cameraZ; the side
  // cheeks and roof make the mount visually continuous instead of floating in space.
  elements.push(cube([6.15, 7.8, cameraZ - 0.25], [6.65, 9.65, cameraZ + 2.1], "frame"));
  elements.push(cube([9.35, 7.8, cameraZ - 0.25], [9.85, 9.65, cameraZ + 2.1], "frame"));
  elements.push(cube([6.15, 9.4, cameraZ - 0.2], [9.85, 9.7, cameraZ + 1.1], "carbon"));
  elements.push(cube([6.0, 8.42, cameraZ + 0.7], [6.18, 8.86, cameraZ + 1.14], "metal"));
  elements.push(cube([9.82, 8.42, cameraZ + 0.7], [10.0, 8.86, cameraZ + 1.14], "metal"));
  elements.push(cube([6.7, by1 + 0.05, bz1 - 0.6], [9.3, 8.0, bz1 + 0.15], "edge"));

  // Rear pod: the battery pigtail and the VTX antenna, tilted back off the top plate.
  const rearZ = bz2 - 0.35;
  elements.push(cube([7.3, 8.14, rearZ - 0.15], [8.7, 8.72, rearZ + 0.55], "plug"));
  const mastPivot = [8, 8.6, rearZ];
  const mastTop = whoop ? 10.9 : 11.6;
  elements.push(cube([7.83, 8.6, rearZ + 0.05], [8.17, mastTop, rearZ + 0.39], "carbon", rot("x", -22.5, mastPivot)));
  elements.push(cube([7.42, mastTop - 0.2, rearZ + 0.06], [8.58, mastTop + 0.06, rearZ + 0.38], "accent", rot("x", -22.5, mastPivot)));

  if (large) landingGear(elements, by1, bz1, bz2);

  return model(textures, elements, {
    gui: { rotation: [35, 225, 0], scale: [0.78, 0.78, 0.78] }
  });
}

const frameSpecs = {
  frame_5inch: { mounts: [[13.5, 8.5, 2.5], [2.5, 8.5, 2.5], [13.5, 8.5, 13.5], [2.5, 8.5, 13.5]], layout: "quad", cameraZ: 4.3 },
  frame_7inch: { mounts: [[15.3, 8.5, 0.75], [0.7, 8.5, 0.75], [14.6, 8.5, 15.15], [1.4, 8.5, 15.15]], layout: "quad", cameraZ: 3.2 },
  frame_tiny_whoop: { mounts: [[11.5, 8, 4.5], [4.5, 8, 4.5], [11.5, 8, 11.5], [4.5, 8, 11.5]], layout: "quad", duct: 2.05, cameraZ: 5.6 },
  frame_cinewhoop: { mounts: [[12.5, 8.5, 3.5], [3.5, 8.5, 3.5], [12.5, 8.5, 12.5], [3.5, 8.5, 12.5]], layout: "quad", duct: 2.65, cameraZ: 5.0 },
  frame_hexa_6inch: { mounts: [[14.4, 8.5, 1.85], [1.6, 8.5, 1.85], [15.5, 8.5, 8], [0.5, 8.5, 8], [14.4, 8.5, 14.15], [1.6, 8.5, 14.15]], layout: "hexa", cameraZ: 3.3 },
  frame_octo_8inch: { mounts: [[13.35, 8.5, 2.65], [2.65, 8.5, 2.65], [15.55, 8.5, 8], [0.45, 8.5, 8], [13.35, 8.5, 13.35], [2.65, 8.5, 13.35], [8, 8.5, 15.55], [8, 8.5, 0.45]], layout: "octo", cameraZ: 3.4 },
  frame_interceptor: { mounts: [[14.3, 8, 12.9], [8, 14.3, 12.9], [1.7, 8, 12.9], [8, 1.7, 12.9]], layout: "quad", cameraZ: 2.6, custom: true }
};
for (const [name, spec] of Object.entries(frameSpecs)) {
  if (!spec.custom) write(name, frameBase(name, spec.mounts, spec.layout, spec.duct, spec.cameraZ));
}

// A torpedo that flies nose-first: one tube, four motors ringing the tail, props standing
// vertical so their thrust runs down the axis. Everything stays axis-aligned or on a legal
// 45 deg element rotation.
function interceptorFrameModel() {
  const elements = [];
  const tube = (z1, z2, r, tex) => {
    elements.push(cube([8 - r, 8 - r, z1], [8 + r, 8 + r, z2], tex));
    elements.push(cube([8 - r, 8 - r, z1], [8 + r, 8 + r, z2], tex, rot("z", 45, [8, 8, (z1 + z2) / 2])));
  };

  tube(0.3, 1.0, 0.6, "nose");
  tube(1.0, 1.9, 1.2, "nose");
  tube(1.9, 3.0, 1.8, "nose");
  tube(3.0, 11.4, 2.15, "carbon");
  tube(11.4, 12.3, 1.95, "carbon");
  tube(12.3, 13.7, 1.75, "dark");
  tube(13.7, 15.2, 1.15, "dark");
  for (const z of [4.6, 7.6, 10.4]) tube(z - 0.16, z + 0.16, 2.3, "edge");
  elements.push(cube([7.45, 7.45, 0.05], [8.55, 8.55, 0.42], "lens"));

  const fz0 = 8.0;
  const fz1 = 11.3;
  const fpivot = [8, 8, (fz0 + fz1) / 2];
  elements.push(cube([3.7, 7.9, fz0], [12.3, 8.1, fz1], "fin", rot("z", 45, fpivot)));
  elements.push(cube([7.9, 3.7, fz0], [8.1, 12.3, fz1], "fin", rot("z", 45, fpivot)));

  const bz0 = 12.35;
  const bz1 = 13.5;
  elements.push(cube([1.4, 7.6, bz0], [14.6, 8.4, bz1], "carbon"));
  elements.push(cube([7.6, 1.4, bz0], [8.4, 14.6, bz1], "carbon"));
  elements.push(cube([1.55, 7.72, bz1], [14.45, 8.28, bz1 + 0.16], "edge"));
  elements.push(cube([7.72, 1.55, bz1], [8.28, 14.45, bz1 + 0.16], "edge"));

  for (const [mx, my] of frameSpecs.frame_interceptor.mounts.map(m => [m[0], m[1]])) {
    elements.push(cube([mx - 1.15, my - 1.15, bz0 - 0.15], [mx + 1.15, my + 1.15, bz0 + 0.5], "plate"));
    elements.push(cube([mx - 1.15, my - 1.15, bz0 - 0.15], [mx + 1.15, my + 1.15, bz0 + 0.5], "plate",
      rot("z", 45, [mx, my, bz0 + 0.18])));
    elements.push(cube([mx - 0.42, my - 0.42, bz0 + 0.5], [mx + 0.42, my + 0.42, bz1 + 0.7], "dark"));
    elements.push(cube([mx - 0.22, my - 0.22, bz0 - 1.9], [mx + 0.22, my + 0.22, bz0 - 0.15], "accent"));
  }

  return model({
    carbon: "fpv:item/frame_5inch",
    plate: "minecraft:block/gray_concrete",
    edge: "minecraft:block/light_gray_concrete",
    dark: "minecraft:block/black_concrete",
    nose: "minecraft:block/red_concrete",
    accent: "minecraft:block/red_concrete",
    fin: "minecraft:block/gray_concrete",
    lens: "minecraft:block/light_blue_stained_glass"
  }, elements, { gui: { rotation: [30, 215, 0], scale: [0.84, 0.84, 0.84] } });
}
write("frame_interceptor", interceptorFrameModel());

function motorModel(name, width, height) {
  const paintedMotor = name === "motor_2806_1300kv" || name === "motor_3115_900kv"
    ? "motor_2306_1700kv"
    : name;
  const main = `fpv:item/${paintedMotor}`;
  const [[x1], [x2]] = centered(width, 0, 0);
  // Attachment convention: y=8 is the motor-pad contact surface.
  const y1 = 8.0;
  const y2 = y1 + height;
  const bellY = y1 + 0.62;
  const ventY1 = bellY + (y2 - bellY) * 0.34;
  const ventY2 = ventY1 + (y2 - bellY) * 0.2;
  const elements = [
    cube([x1 - 0.4, y1, x1 - 0.4], [x2 + 0.4, y1 + 0.2, x2 + 0.4], "dark"),
    cube([x1 + 0.12, y1 + 0.2, x1 + 0.12], [x2 - 0.12, bellY, x2 - 0.12], "metal")
  ];
  // Stator windings peeking out of the gap between the base and the bell.
  const inset = 0.22;
  elements.push(cube([x1 + inset, y1 + 0.28, x1 - 0.04], [x2 - inset, bellY - 0.08, x1 + 0.1], "copper"));
  elements.push(cube([x1 + inset, y1 + 0.28, x2 - 0.1], [x2 - inset, bellY - 0.08, x2 + 0.04], "copper"));
  elements.push(cube([x1 - 0.04, y1 + 0.28, x1 + inset], [x1 + 0.1, bellY - 0.08, x2 - inset], "copper"));
  elements.push(cube([x2 - 0.1, y1 + 0.28, x1 + inset], [x2 + 0.04, bellY - 0.08, x2 - inset], "copper"));

  elements.push(cube([x1, bellY, x1], [x2, y2, x2], "main"));
  // Cooling slots around the bell.
  const slot = 0.3;
  elements.push(cube([x1 + slot, ventY1, x1 - 0.03], [x2 - slot, ventY2, x1 + 0.07], "dark"));
  elements.push(cube([x1 + slot, ventY1, x2 - 0.07], [x2 - slot, ventY2, x2 + 0.03], "dark"));
  elements.push(cube([x1 - 0.03, ventY1, x1 + slot], [x1 + 0.07, ventY2, x2 - slot], "dark"));
  elements.push(cube([x2 - 0.07, ventY1, x1 + slot], [x2 + 0.03, ventY2, x2 - slot], "dark"));

  elements.push(cube([x1 + 0.16, y2, x1 + 0.16], [x2 - 0.16, y2 + 0.17, x2 - 0.16], "metal"));
  elements.push(cube([7.82, y2 + 0.17, 7.82], [8.18, y2 + 0.55, 8.18], "metal"));
  elements.push(cube([7.66, y2 + 0.42, 7.66], [8.34, y2 + 0.72, 8.34], "dark"));
  // Three-wire bundle leaving the base towards the frame centre.
  elements.push(cube([7.62, y1 + 0.02, x1 - 0.95], [8.38, y1 + 0.18, x1 + 0.1], "dark"));
  elements.push(cube([7.72, y1 + 0.04, x1 - 0.9], [7.92, y1 + 0.16, x1 + 0.05], "copper"));
  return model({ main, dark: "minecraft:block/black_concrete", metal: "minecraft:block/iron_block", copper: "minecraft:block/copper_block" }, elements);
}

for (const [name, width, height] of [
  ["motor_1103_8000kv", 1.0, 1.15], ["motor_1404_4500kv", 1.3, 1.35],
  ["motor_2207_2450kv", 1.9, 2.0], ["motor_2306_1700kv", 2.0, 1.85],
  ["motor_2806_1300kv", 2.35, 1.9], ["motor_3115_900kv", 2.65, 2.8]
]) write(name, motorModel(name, width, height));

const BLADE_DIRS = {
  0: { span: [0, 1], lead: [1, 0] },
  90: { span: [1, 0], lead: [0, -1] },
  180: { span: [0, -1], lead: [-1, 0] },
  270: { span: [-1, 0], lead: [0, 1] }
};

// Blades are built along a cardinal axis so the only rotation left for an element is the
// azimuth remainder; the pitch is stepped instead of turned, which keeps it solid from
// every viewing angle.
function bladeElements(elements, base, residual, radius, texture) {
  const dir = BLADE_DIRS[base];
  const turn = residual === 0 ? null : rot("y", residual, [8, 8, 8]);
  const rootZ = Math.max(0.5, radius * 0.2);
  const mid = radius * 0.58;
  const chordIn = radius * 0.3;
  const chordOut = radius * 0.2;
  const riseIn = radius * 0.075;
  const riseOut = radius * 0.05;
  const overlap = 0.12;

  const box = (t0, t1, c0, c1, y0, y1) => {
    const xs = [];
    const zs = [];
    for (const t of [t0, t1]) {
      for (const c of [c0, c1]) {
        xs.push(8 + dir.span[0] * t + dir.lead[0] * c);
        zs.push(8 + dir.span[1] * t + dir.lead[1] * c);
      }
    }
    elements.push(cube([Math.min(...xs), y0, Math.min(...zs)],
      [Math.max(...xs), y1, Math.max(...zs)], texture, turn));
  };

  box(rootZ, mid + 0.05, -overlap, chordIn, 8, 8 + riseIn);
  box(rootZ, mid + 0.05, -chordIn, overlap, 8 - riseIn, 8);
  box(mid, radius, -overlap, chordOut, 8, 8 + riseOut);
  box(mid, radius, -chordOut, overlap, 8 - riseOut, 8);
}

function propModel(name, diameter, blades) {
  const propColors = {
    prop_65mm: [228, 226, 214],
    prop_3x2x3: [198, 204, 212],
    prop_5x4x2: [116, 124, 138],
    prop_5x4x3: [98, 108, 126]
  };
  const bladeTextures = {
    prop_65mm: "minecraft:block/white_concrete",
    prop_3x2x3: "minecraft:block/light_gray_concrete",
    prop_5x4x2: "minecraft:block/gray_concrete",
    prop_5x4x3: "minecraft:block/black_concrete"
  };
  makePropTexture(name, blades, propColors[name]);
  const radius = diameter / 2;
  const azimuths = blades === 2
    ? [[0, 0], [180, 0]]
    : blades === 3
      ? [[0, 0], [90, 22.5], [180, 45]]
      : [[0, 0], [90, 0], [180, 0], [270, 0]];

  const hubHalf = Math.max(0.5, radius * 0.17);
  const elements = [];
  for (const [base, residual] of azimuths) bladeElements(elements, base, residual, radius, "blade");
  elements.push(cube([8 - hubHalf, 7.72, 8 - hubHalf], [8 + hubHalf, 8.36, 8 + hubHalf], "hub"));
  elements.push(cube([8 - hubHalf, 7.72, 8 - hubHalf], [8 + hubHalf, 8.36, 8 + hubHalf], "hub", rot("y", 45, [8, 8.04, 8])));
  elements.push(cube([8 - hubHalf * 0.55, 8.36, 8 - hubHalf * 0.55], [8 + hubHalf * 0.55, 8.62, 8 + hubHalf * 0.55], "nut"));
  elements.push(cube([8 - hubHalf * 0.55, 8.36, 8 - hubHalf * 0.55], [8 + hubHalf * 0.55, 8.62, 8 + hubHalf * 0.55], "nut", rot("y", 45, [8, 8.49, 8])));

  return model({ blade: bladeTextures[name], hub: "minecraft:block/iron_block", nut: "minecraft:block/black_concrete" }, elements, {
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
    // Heat-shrink lips at both ends of the wrap.
    cube([x1 - 0.06, y1 - 0.04, z1 + 0.15], [x2 + 0.06, y1 + 0.16, z2 - 0.15], "strap"),
    cube([x1 - 0.06, y2 - 0.16, z1 + 0.15], [x2 + 0.06, y2 + 0.04, z2 - 0.15], "strap"),
    cube([x1 - 0.08, y1 + height * 0.3, 7.55], [x2 + 0.08, y1 + height * 0.48, 8.45], "strap"),
    cube([x1 - 0.08, y1 + height * 0.58, z1 + length * 0.24], [x2 + 0.08, y1 + height * 0.76, z1 + length * 0.24 + 0.9], "strap"),
    cube([x1 + 0.2, y2, z1 + 0.35], [x2 - 0.2, y2 + 0.15, z2 - 0.35], "label"),
    // XT60 plug with its two contacts, plus the balance lead alongside it.
    cube([x2, y1 + 0.2, 7.45], [x2 + 0.7, y1 + 0.95, 8.55], "plug"),
    cube([x2 + 0.7, y1 + 0.35, 7.62], [x2 + 1.0, y1 + 0.8, 7.92], "contact"),
    cube([x2 + 0.7, y1 + 0.35, 8.08], [x2 + 1.0, y1 + 0.8, 8.38], "contact"),
    cube([x2 + 0.12, y1 + 0.98, 7.6], [x2 + 0.58, y1 + 1.2, 8.4], "accent"),
    cube([x1 - 0.5, y1 + 0.3, 8.7], [x1, y1 + 0.66, 9.4], "lead")
  ];
  for (let i = 1; i < cells; i++) {
    const y = y1 + height * i / cells;
    elements.push(cube([x1 - 0.03, y - 0.04, z1 - 0.03], [x2 + 0.03, y + 0.04, z2 + 0.03], "edge"));
  }
  return model({ pack: `fpv:item/${name}`, strap: "minecraft:block/black_concrete", label: "minecraft:block/yellow_concrete", plug: "minecraft:block/yellow_concrete", contact: "minecraft:block/black_concrete", accent: "minecraft:block/red_concrete", lead: "minecraft:block/white_concrete", edge: "minecraft:block/black_concrete" }, elements);
}

write("battery_1s_300", batteryModel("battery_1s_300", 2.2, 1.5, 3.6, 1));
write("battery_4s_650", batteryModel("battery_4s_650", 3.0, 2.2, 5.0, 4));
write("battery_4s_1300", batteryModel("battery_4s_1300", 3.8, 2.8, 6.8, 4));
write("battery_6s_1100", batteryModel("battery_6s_1100", 4.0, 3.4, 6.2, 6));
write("battery_6s_5000", batteryModel("battery_6s_5000", 4.8, 4.1, 8.2, 6));

function cameraModel(name, width, height, depth, wide) {
  const [[x1], [x2]] = centered(width, 0, 0);
  // The rear face at z=8 is the pivot captured by FrameSpecs.cameraMount().
  const y1 = 7.0;
  const y2 = y1 + height;
  const z1 = 8 - depth;
  const z2 = 8.0;
  const midY = (y1 + y2) / 2;
  const lensHalf = Math.min(width, height) * 0.33;
  const elements = [
    cube([x1, y1, z1 + 0.2], [x2, y2, z2], "shell", null, { north: "#dark" }),
    cube([x1 + 0.14, y1 + 0.14, z1], [x2 - 0.14, y2 - 0.14, z1 + 0.2], "dark"),
    // Lens barrel: two steps forward, then a retaining ring and the glass.
    cube([8 - lensHalf - 0.24, midY - lensHalf - 0.24, z1 - 0.46], [8 + lensHalf + 0.24, midY + lensHalf + 0.24, z1], "dark"),
    cube([8 - lensHalf, midY - lensHalf, z1 - 0.94], [8 + lensHalf, midY + lensHalf, z1 - 0.46], "dark"),
    cube([8 - lensHalf - 0.1, midY - lensHalf - 0.1, z1 - 1.06], [8 + lensHalf + 0.1, midY + lensHalf + 0.1, z1 - 0.94], "metal"),
    cube([8 - lensHalf + 0.18, midY - lensHalf + 0.18, z1 - 1.12], [8 + lensHalf - 0.18, midY + lensHalf - 0.18, z1 - 1.04], "lens"),
    // Side brackets and the tilt screws they pivot on.
    cube([x1 - 0.26, y1 + 0.32, z1 + 0.55], [x1, y2 - 0.32, z2 - 0.25], "metal"),
    cube([x2, y1 + 0.32, z1 + 0.55], [x2 + 0.26, y2 - 0.32, z2 - 0.25], "metal"),
    cube([x1 - 0.38, midY - 0.2, 7.35], [x1 - 0.12, midY + 0.2, 7.75], "dark"),
    cube([x2 + 0.12, midY - 0.2, 7.35], [x2 + 0.38, midY + 0.2, 7.75], "dark"),
    // Rear connector, status LED and the short pigtail into the stack.
    cube([x1 + 0.35, y1 + 0.25, z2], [x2 - 0.35, y1 + 1.0, z2 + 0.3], "dark"),
    cube([x1 + 0.4, y2 - 0.3, z2 - 0.06], [x1 + 0.78, y2 - 0.06, z2 + 0.01], "accent"),
    cube([7.72, y1 + 0.4, z2 + 0.3], [8.28, y1 + 0.85, z2 + 0.85], "metal")
  ];
  return model({ shell: `fpv:item/${name}`, dark: "minecraft:block/black_concrete", lens: "minecraft:block/blue_stained_glass", metal: "minecraft:block/iron_block", accent: wide ? "minecraft:block/orange_concrete" : "minecraft:block/lime_concrete" }, elements);
}

write("camera_standard", cameraModel("camera_standard", 2.4, 2.2, 2.2, false));
write("camera_wide", cameraModel("camera_wide", 3.2, 2.45, 2.4, true));

function boardModel(name, width, length, highPower = false, flightController = false) {
  const [[x1, , z1], [x2, , z2]] = centered(width, 0, 0, length);
  const elements = [
    cube([x1, 7.55, z1], [x2, 8.0, z2], "pcb"),
    cube([7.05, 8.0, 7.05], [8.35, 8.75, 8.35], "chip"),
    cube([8.7, 8.0, 7.25], [x2 - 0.25, 8.45, 8.0], "metal"),
    cube([x1 + 0.25, 8.0, 8.65], [x1 + 1.0, 8.55, z2 - 0.25], "copper"),
    // Solder-pad row along the rear edge.
    cube([x1 + 0.6, 7.98, z2 - 0.55], [x2 - 0.6, 8.06, z2 - 0.2], "copper")
  ];
  for (const [x, z] of [[x1 + 0.28, z1 + 0.28], [x2 - 0.28, z1 + 0.28], [x1 + 0.28, z2 - 0.28], [x2 - 0.28, z2 - 0.28]]) {
    elements.push(cube([x - 0.13, 7.45, z - 0.13], [x + 0.13, 8.12, z + 0.13], "metal"));
    elements.push(cube([x - 0.2, 7.38, z - 0.2], [x + 0.2, 7.5, z + 0.2], "chip"));
  }
  if (flightController) {
    // USB port on the side plus the capacitor every FC stack carries.
    elements.push(cube([x1 - 0.22, 7.98, 7.6], [x1 + 0.5, 8.34, 8.4], "metal"));
    const capX = x2 - 0.9;
    const capZ = z1 + 0.9;
    elements.push(cube([capX - 0.42, 8.0, capZ - 0.42], [capX + 0.42, 9.0, capZ + 0.42], "cap"));
    elements.push(cube([capX - 0.42, 8.0, capZ - 0.42], [capX + 0.42, 9.0, capZ + 0.42], "cap", rot("y", 45, [capX, 8.5, capZ])));
    elements.push(cube([capX - 0.3, 8.98, capZ - 0.3], [capX + 0.3, 9.06, capZ + 0.3], "metal"));
  }
  if (highPower) {
    elements.push(cube([6.8, 8.75, 6.9], [9.2, 9.35, 9.1], "sink"));
    for (let x = 7.0; x <= 9.0; x += 0.5) elements.push(cube([x, 9.35, 7.0], [x + 0.18, 9.7, 9.0], "metal"));
  }
  if (!flightController) {
    elements.push(cube([x2 - 0.25, 8.05, 8.25], [x2 + 0.2, 8.5, 8.7], "antenna"));
    elements.push(cube([x2 + 0.05, 8.28, 8.42], [x2 + 2.0, 8.48, 8.62], "antenna", rot("z", -22.5, [x2 + 0.05, 8.38, 8.52])));
    // Channel button and its status LED.
    elements.push(cube([x1 + 0.4, 8.0, z1 + 0.5], [x1 + 0.85, 8.22, z1 + 0.95], "metal"));
    elements.push(cube([x1 + 1.15, 8.0, z1 + 0.6], [x1 + 1.45, 8.14, z1 + 0.9], "led"));
  }
  return model({ pcb: `fpv:item/${name}`, chip: "minecraft:block/black_concrete", metal: "minecraft:block/iron_block", copper: "minecraft:block/copper_block", sink: "minecraft:block/cut_copper", antenna: "minecraft:block/red_concrete", cap: "minecraft:block/blue_concrete", led: "minecraft:block/lime_concrete" }, elements);
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

const bolts = (y1, y2, spots) => spots.map(([x, z]) =>
  cube([x - 0.3, y1, z - 0.3], [x + 0.3, y2, z + 0.3], "metal"));

write("module_payload_bay", model(moduleTextures, [
  cube([4.6, 7.62, 4.4], [11.4, 8.0, 11.6], "carbon"),
  ...bolts(7.34, 7.62, [[5.3, 5.1], [10.7, 5.1], [5.3, 10.9], [10.7, 10.9]]),
  cube([5.0, 6.1, 4.8], [11.0, 7.62, 11.2], "dark_metal"),
  cube([4.82, 6.0, 5.0], [5.0, 7.6, 11.0], "metal"),
  cube([11.0, 6.0, 5.0], [11.18, 7.6, 11.0], "metal"),
  cube([5.05, 7.0, 5.6], [10.95, 7.2, 5.75], "metal"),
  cube([5.05, 7.0, 10.25], [10.95, 7.2, 10.4], "metal"),
  cube([6.6, 6.3, 4.32], [9.4, 7.5, 5.3], "metal"),
  cube([7.5, 7.5, 4.55], [8.5, 7.74, 5.05], "carbon"),
  cube([7.82, 6.02, 4.55], [8.18, 6.3, 5.05], "dark_metal"),
  cube([7.4, 5.86, 4.6], [8.6, 6.04, 5.0], "carbon"),
  cube([5.0, 5.78, 5.2], [8.02, 6.1, 10.8], "carbon", rot("z", 22.5, [5.05, 6.05, 8])),
  cube([7.98, 5.78, 5.2], [11.0, 6.1, 10.8], "carbon", rot("z", -22.5, [10.95, 6.05, 8])),
  cube([6.25, 5.4, 6.1], [7.35, 6.12, 9.9], "red"),
  cube([8.65, 5.4, 6.1], [9.75, 6.12, 9.9], "red"),
  cube([6.42, 5.56, 5.55], [7.18, 5.98, 6.1], "metal"),
  cube([8.82, 5.56, 5.55], [9.58, 5.98, 6.1], "metal"),
  cube([6.45, 5.5, 9.9], [7.15, 6.06, 10.35], "dark_metal"),
  cube([8.85, 5.5, 9.9], [9.55, 6.06, 10.35], "dark_metal"),
  cube([7.4, 6.4, 11.2], [8.6, 7.2, 11.48], "metal"),
  cube([7.68, 6.62, 11.48], [8.32, 6.98, 11.62], "orange"),
  cube([7.74, 6.68, 11.62], [8.26, 6.92, 11.7], "lens"),
  cube([6.9, 7.66, 3.98], [9.1, 7.98, 4.4], "orange")
]));

write("module_warhead", model(moduleTextures, [
  cube([7.8, 6.15, 1.05], [8.2, 6.65, 1.8], "metal"),
  cube([7.55, 5.9, 1.8], [8.45, 6.9, 2.6], "olive"),
  cube([7.25, 5.6, 2.6], [8.75, 7.2, 3.5], "olive"),
  cube([6.9, 5.25, 3.5], [9.1, 7.55, 4.4], "olive"),
  cube([6.88, 5.23, 4.4], [9.12, 7.57, 4.92], "yellow"),
  cube([6.65, 5.0, 4.92], [9.35, 7.8, 7.7], "olive"),
  cube([6.62, 5.55, 5.35], [9.38, 6.05, 5.68], "red"),
  cube([6.62, 5.55, 6.95], [9.38, 6.05, 7.28], "red"),
  cube([7.35, 5.75, 7.7], [8.65, 7.05, 12.5], "dark_metal"),
  cube([7.18, 5.58, 9.3], [8.82, 7.22, 9.75], "metal"),
  cube([7.72, 7.05, 10.5], [8.28, 8.0, 12.7], "olive"),
  cube([7.72, 4.8, 10.5], [8.28, 5.75, 12.7], "olive"),
  cube([6.28, 6.12, 10.5], [7.35, 6.68, 12.7], "olive"),
  cube([8.65, 6.12, 10.5], [9.72, 6.68, 12.7], "olive"),
  cube([6.55, 7.8, 5.3], [9.45, 7.98, 9.3], "carbon"),
  ...bolts(7.6, 7.8, [[7.05, 5.75], [8.95, 5.75], [7.05, 8.85], [8.95, 8.85]]),
  cube([6.55, 7.5, 5.9], [9.45, 7.82, 6.25], "metal"),
  cube([7.6, 7.8, 9.6], [8.4, 8.02, 10.6], "red"),
  cube([7.86, 6.9, 4.95], [8.14, 7.86, 5.3], "red")
]));

write("module_dart_gun", model(moduleTextures, [
  cube([6.3, 7.6, 5.0], [9.7, 8.0, 10.9], "carbon"),
  ...bolts(7.32, 7.6, [[6.9, 5.6], [9.1, 5.6], [6.9, 10.3], [9.1, 10.3]]),
  cube([6.1, 5.7, 5.6], [9.9, 7.6, 10.4], "dark_metal"),
  cube([6.02, 6.2, 6.0], [9.98, 7.05, 6.5], "metal"),
  cube([6.02, 6.2, 9.4], [9.98, 7.05, 9.9], "metal"),
  cube([7.3, 6.2, 1.7], [8.7, 7.4, 5.6], "dark_metal"),
  cube([7.12, 6.38, 1.7], [8.88, 7.22, 5.6], "dark_metal"),
  cube([7.1, 6.0, 3.3], [8.9, 7.6, 3.72], "metal"),
  cube([7.1, 6.0, 4.7], [8.9, 7.6, 5.12], "metal"),
  cube([7.14, 6.04, 1.0], [8.86, 7.56, 1.72], "metal"),
  cube([7.44, 6.34, 0.86], [8.56, 7.26, 1.02], "dark_metal"),
  cube([7.6, 6.5, 0.8], [8.4, 7.1, 0.9], "carbon"),
  cube([7.05, 3.95, 6.3], [8.95, 5.72, 9.1], "dark_metal"),
  cube([6.95, 5.3, 6.2], [9.05, 5.75, 9.2], "metal"),
  cube([7.35, 3.55, 6.6], [8.65, 3.98, 8.8], "brown"),
  cube([7.55, 3.35, 6.75], [8.45, 3.58, 8.65], "metal"),
  cube([6.55, 5.05, 9.2], [9.45, 6.3, 10.7], "metal"),
  cube([7.62, 4.6, 9.5], [8.38, 5.1, 10.3], "dark_metal"),
  cube([8.85, 6.9, 6.1], [9.86, 7.6, 7.0], "dark_metal"),
  cube([9.86, 7.05, 6.3], [9.98, 7.45, 6.8], "lens"),
  cube([6.25, 7.34, 7.2], [9.75, 7.62, 7.8], "orange")
]));

write("module_storage", model(moduleTextures, [
  cube([4.4, 3.9, 4.5], [11.6, 7.5, 11.5], "brown"),
  cube([4.15, 7.5, 4.25], [11.85, 8.0, 11.75], "dark_metal"),
  cube([4.3, 3.66, 4.4], [11.7, 3.9, 11.6], "metal"),
  cube([4.32, 3.8, 4.42], [5.28, 7.62, 5.38], "carbon"),
  cube([10.72, 3.8, 4.42], [11.68, 7.62, 5.38], "carbon"),
  cube([4.32, 3.8, 10.62], [5.28, 7.62, 11.58], "carbon"),
  cube([10.72, 3.8, 10.62], [11.68, 7.62, 11.58], "carbon"),
  cube([4.34, 5.05, 4.44], [11.66, 5.5, 11.56], "dark_metal"),
  cube([4.34, 6.5, 4.44], [11.66, 6.95, 11.56], "dark_metal"),
  cube([6.9, 4.4, 4.28], [9.1, 5.05, 4.5], "metal"),
  cube([7.35, 4.55, 4.14], [8.65, 4.9, 4.3], "orange"),
  cube([6.9, 6.95, 4.28], [9.1, 7.5, 4.5], "metal"),
  cube([6.4, 8.0, 6.9], [6.9, 8.42, 9.1], "metal"),
  cube([9.1, 8.0, 6.9], [9.6, 8.42, 9.1], "metal"),
  cube([6.4, 8.42, 7.5], [9.6, 8.72, 8.5], "carbon"),
  cube([5.4, 7.98, 5.6], [6.6, 8.16, 6.4], "orange"),
  cube([5.55, 8.16, 5.75], [6.45, 8.24, 6.25], "carbon")
]));

write("module_targeting", model(moduleTextures, [
  cube([6.2, 6.9, 6.2], [9.8, 7.6, 9.8], "dark_metal"),
  cube([6.6, 7.6, 6.6], [9.4, 8.05, 9.4], "carbon"),
  cube([5.9, 4.5, 5.9], [10.1, 6.9, 10.1], "olive"),
  cube([6.35, 4.05, 6.35], [9.65, 4.55, 9.65], "dark_metal"),
  cube([6.5, 4.9, 5.7], [9.5, 6.4, 5.94], "metal"),
  cube([6.8, 5.15, 5.56], [9.2, 6.15, 5.74], "lens"),
  cube([7.3, 5.4, 5.44], [8.7, 5.9, 5.6], "orange"),
  cube([9.3, 5.2, 9.9], [9.9, 6.2, 10.35], "red")
]));

// A paper card with the aircraft printed on it — flat item art, not a cube model.
function makeDroneCardTexture() {
  const size = 16;
  const px = Buffer.alloc(size * size * 4);
  const set = (x, y, r, g, b) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4;
    px[i] = r; px[i + 1] = g; px[i + 2] = b; px[i + 3] = 255;
  };
  for (let y = 1; y < 15; y++) {
    for (let x = 2; x < 14; x++) {
      const edge = x === 2 || x === 13 || y === 1 || y === 14;
      set(x, y, ...(edge ? [58, 50, 42] : [228, 218, 197]));
    }
  }
  for (let x = 4; x < 12; x++) set(x, 12, 152, 141, 122);
  for (let x = 4; x < 10; x++) set(x, 13, 152, 141, 122);
  const ink = (x, y) => set(x, y, 38, 38, 45);
  for (const [x, y] of [[7, 6], [8, 6], [7, 7], [8, 7], [6, 5], [9, 5], [6, 8], [9, 8],
                        [4, 4], [5, 4], [10, 4], [11, 4], [4, 9], [5, 9], [10, 9], [11, 9],
                        [7, 4], [8, 4]]) ink(x, y);
  for (const [x, y] of [[11, 2], [12, 2], [12, 3]]) set(x, y, 176, 58, 48);
  writeRgbaPng("drone_card", size, size, px);
}
makeDroneCardTexture();
write("drone_card", { parent: "minecraft:item/generated", textures: { layer0: "fpv:item/parts/drone_card" } });

const rotY = (dx, dz, angle) => {
  const a = (angle * Math.PI) / 180;
  const c = Math.cos(a);
  const s = Math.sin(a);
  return [dx * c + dz * s, -dx * s + dz * c];
};

// Wing tips point along the flight path, so the winglet is emitted unrotated at the
// already-swept tip instead of inheriting the panel's sweep.
function winglet(elements, side, lead, trail, height, baseY = 8, half = 0.16) {
  const outer = Math.max(side * (lead[0] - 8), side * (trail[0] - 8));
  const z1 = Math.min(lead[1], trail[1]);
  const z2 = Math.max(lead[1], trail[1]);
  const xr = (a, b) => {
    const p = 8 + side * a;
    const q = 8 + side * b;
    return [Math.min(p, q), Math.max(p, q)];
  };
  // The tip plate swallows the panel's slanted end face and every other piece is stepped
  // strictly inside it, so no two surfaces ever land on the same plane and flicker.
  const pad = half + 0.09;
  const [px, py] = xr(outer - 1.25, outer + 0.1);
  const [fx, fy] = xr(outer - 0.52, outer - 0.04);
  const [cx, cy] = xr(outer - 0.44, outer - 0.12);
  elements.push(cube([px, baseY - pad, z1 - 0.06], [py, baseY + pad, z2 + 0.06], "wing"));
  elements.push(cube([fx, baseY + pad - 0.12, z1 + 0.16], [fy, baseY + height, z2 - 0.06], "wing"));
  elements.push(cube([cx, baseY + height - 0.04, z1 + 0.34], [cy, baseY + height + 0.34, z2 - 0.22], "accent"));
  elements.push(cube([cx, baseY - 0.66, z1 + 0.32], [cy, baseY - pad + 0.1, z2 - 0.36], "wing"));
}

const SPINNER_DIRS = [[[1, 0], [0, 1]], [[0, 1], [-1, 0]], [[-1, 0], [0, -1]], [[0, -1], [1, 0]]];

function spinnerBlades(elements, zPlane, radius, tractor, cy = 8.08) {
  const t = 0.11;
  const root = 0.22;
  const mid = radius * 0.6;
  const cIn = radius * 0.26;
  const cOut = radius * 0.17;
  const f = tractor ? -1 : 1;
  const box = (sp, ld, s0, s1, c0, c1, z0, z1) => {
    const ax = 8 + sp[0] * s0 + ld[0] * c0;
    const ay = cy + sp[1] * s0 + ld[1] * c0;
    const bx = 8 + sp[0] * s1 + ld[0] * c1;
    const by = cy + sp[1] * s1 + ld[1] * c1;
    elements.push(cube([Math.min(ax, bx), Math.min(ay, by), Math.min(z0, z1)],
      [Math.max(ax, bx), Math.max(ay, by), Math.max(z0, z1)], "prop"));
  };
  for (const [sp, ld] of SPINNER_DIRS) {
    box(sp, ld, root, mid + 0.05, -0.09, cIn, zPlane, zPlane + f * t * 1.5);
    box(sp, ld, root, mid + 0.05, -cIn, 0.09, zPlane - f * t * 1.5, zPlane);
    box(sp, ld, mid, radius, -0.09, cOut, zPlane, zPlane + f * t);
    box(sp, ld, mid, radius, -cOut, 0.09, zPlane - f * t, zPlane);
  }
}

// A plane frame item is the same airframe without its power train, so the models are
// generated twice with the prop and motor suppressed the second time.
let SKIP_PROP = false;

function airframe(build) {
  SKIP_PROP = true;
  try {
    return build();
  } finally {
    SKIP_PROP = false;
  }
}

function pusherProp(elements, z, radius = 3.2) {
  if (SKIP_PROP) return;
  elements.push(cube([7.44, 7.6, z - 0.8], [8.56, 8.56, z], "metal"));
  elements.push(cube([7.72, 7.8, z], [8.44, 8.36, z + 0.44], "dark"));
  elements.push(cube([7.72, 7.8, z], [8.44, 8.36, z + 0.44], "dark", rot("z", 45, [8.08, 8.08, z + 0.22])));
  spinnerBlades(elements, z + 0.2, radius, false);
  elements.push(cube([7.86, 7.94, z + 0.44], [8.3, 8.22, z + 0.74], "metal"));
}

function tractorProp(elements, z, radius = 3.0) {
  if (SKIP_PROP) return;
  elements.push(cube([7.46, 7.62, z], [8.54, 8.58, z + 0.8], "metal"));
  elements.push(cube([7.72, 7.8, z - 0.44], [8.44, 8.36, z], "dark"));
  elements.push(cube([7.72, 7.8, z - 0.44], [8.44, 8.36, z], "dark", rot("z", 45, [8.08, 8.08, z - 0.22])));
  spinnerBlades(elements, z - 0.2, radius, true);
  elements.push(cube([7.62, 7.72, z - 0.8], [8.54, 8.44, z - 0.38], "nose"));
  elements.push(cube([7.78, 7.88, z - 1.06], [8.38, 8.28, z - 0.76], "nose"));
}

function planeTextures(overrides) {
  return Object.assign({
    body: "minecraft:block/light_gray_concrete",
    wing: "minecraft:block/white_concrete",
    accent: "minecraft:block/red_concrete",
    canopy: "minecraft:block/black_concrete",
    nose: "minecraft:block/orange_concrete",
    dark: "minecraft:block/black_concrete",
    metal: "minecraft:block/iron_block",
    prop: "minecraft:block/gray_concrete",
    lens: "minecraft:block/light_blue_stained_glass",
    warhead: "minecraft:block/green_concrete",
    yellow: "minecraft:block/yellow_concrete"
  }, overrides);
}

const planeDisplay = { gui: { rotation: [35, 225, 0], scale: [0.72, 0.72, 0.72] } };

function shiftElements(list, dx, dy, dz = 0) {
  for (const el of list) {
    el.from = [el.from[0] + dx, el.from[1] + dy, el.from[2] + dz];
    el.to = [el.to[0] + dx, el.to[1] + dy, el.to[2] + dz];
    if (el.rotation) {
      el.rotation.origin = [el.rotation.origin[0] + dx, el.rotation.origin[1] + dy, el.rotation.origin[2] + dz];
    }
  }
  return list;
}

function pusherPropAt(elements, z, radius, cx, cy) {
  const tmp = [];
  pusherProp(tmp, z, radius);
  elements.push(...shiftElements(tmp, cx - 8, cy - 8.08));
}

function tractorPropAt(elements, z, radius, cx, cy) {
  const tmp = [];
  tractorProp(tmp, z, radius);
  elements.push(...shiftElements(tmp, cx - 8, cy - 8.08));
}

// Delta panel built from spanwise strips so the leading edge can sweep while the trailing
// edge stays straight.
function deltaPanel(elements, side, span, rootX, leadRoot, leadTip, trailZ, baseY, halfRoot, halfTip, steps = 8) {
  for (let i = 0; i < steps; i++) {
    const f0 = i / steps;
    const f1 = (i + 1) / steps;
    const fm = (f0 + f1) / 2;
    const x0 = rootX + f0 * (span - rootX);
    const x1 = rootX + f1 * (span - rootX);
    const lead = leadRoot + fm * (leadTip - leadRoot);
    const half = halfRoot + (halfTip - halfRoot) * fm;
    const a = 8 + Math.min(side * x0, side * x1);
    const b = 8 + Math.max(side * x0, side * x1);
    elements.push(cube([a, baseY - half, lead], [b, baseY + half, trailZ], "wing"));
    elements.push(cube([a, baseY - half + 0.03, lead - 0.24], [b, baseY + half - 0.03, lead + 0.08], "dark"));
    if (i > 1) {
      elements.push(cube([a, baseY - half + 0.04, trailZ - 0.06], [b, baseY + half - 0.04, trailZ + 0.5], "accent"));
    }
  }
}

// Rectangular panel with a leading-edge strip and an aileron strip, used by the big wings.
function straightPanel(elements, side, x0, x1, leadZ, chord, baseY, half) {
  const a = 8 + Math.min(side * x0, side * x1);
  const b = 8 + Math.max(side * x0, side * x1);
  elements.push(cube([a, baseY - half, leadZ], [b, baseY + half, leadZ + chord], "wing"));
  elements.push(cube([a, baseY - half + 0.03, leadZ - 0.2], [b, baseY + half - 0.03, leadZ + 0.06], "dark"));
  elements.push(cube([a, baseY - half + 0.05, leadZ + chord - 0.06], [b, baseY + half - 0.05, leadZ + chord + 0.45], "accent"));
}

function planeShahedModel() {
  const elements = [];
  const span = 7.7;

  elements.push(cube([7.1, 7.24, 3.0], [8.9, 9.06, 11.8], "body"));
  elements.push(cube([7.34, 7.46, 1.85], [8.66, 8.84, 3.0], "warhead"));
  elements.push(cube([7.62, 7.7, 1.0], [8.38, 8.56, 1.85], "warhead"));
  elements.push(cube([7.78, 7.86, 0.5], [8.22, 8.4, 1.0], "nose"));
  elements.push(cube([7.06, 7.06, 4.2], [8.94, 7.3, 10.6], "dark"));
  elements.push(cube([7.16, 6.66, 5.0], [8.84, 7.1, 8.2], "yellow"));

  for (const side of [-1, 1]) {
    deltaPanel(elements, side, span, 0.9, 4.1, 10.2, 11.9, 8.2, 0.32, 0.16, 9);
    const tipX = 8 + side * span;
    const a = Math.min(tipX, tipX - side * 0.34);
    const b = Math.max(tipX, tipX - side * 0.34);
    elements.push(cube([a, 6.5, 10.1], [b, 8.4, 12.0], "wing"));
    elements.push(cube([a, 6.5, 11.5], [b, 8.4, 12.05], "accent"));
    elements.push(cube([a + 0.02, 8.2, 10.3], [b - 0.02, 9.5, 12.0], "wing"));
  }

  elements.push(cube([7.4, 9.06, 9.0], [8.6, 10.5, 12.1], "dark"));
  elements.push(cube([7.56, 10.0, 8.6], [8.44, 10.44, 9.2], "metal"));
  pusherPropAt(elements, 12.4, 2.5, 8, 9.8);

  return model(planeTextures({
    body: "minecraft:block/light_gray_concrete",
    wing: "minecraft:block/light_gray_concrete",
    accent: "minecraft:block/red_concrete",
    warhead: "minecraft:block/gray_concrete",
    nose: "minecraft:block/black_concrete",
    dark: "minecraft:block/black_concrete",
    yellow: "minecraft:block/yellow_concrete"
  }), elements, planeDisplay);
}

function planeLongRangeModel() {
  const elements = [];
  const span = 8.6;
  const baseY = 9.3;

  elements.push(cube([6.94, 7.16, 2.9], [9.06, 9.24, 12.9], "body"));
  elements.push(cube([7.2, 7.42, 1.6], [8.8, 8.96, 2.9], "nose"));
  elements.push(cube([7.5, 7.66, 0.75], [8.5, 8.7, 1.6], "nose"));
  elements.push(cube([7.24, 9.24, 3.2], [8.76, 9.74, 5.0], "canopy"));
  elements.push(cube([6.9, 6.88, 4.0], [9.1, 7.2, 11.4], "warhead"));
  elements.push(cube([6.86, 6.48, 5.0], [9.14, 6.94, 6.0], "yellow"));
  elements.push(cube([6.86, 6.48, 9.0], [9.14, 6.94, 10.0], "yellow"));

  elements.push(cube([6.5, 9.06, 5.2], [9.5, baseY + 0.3, 8.6], "body"));
  for (const side of [-1, 1]) {
    straightPanel(elements, side, 0.0, 3.4, 5.3, 3.3, baseY, 0.28);
    straightPanel(elements, side, 3.4, 6.2, 5.4, 3.0, baseY, 0.24);
    straightPanel(elements, side, 6.2, span, 5.6, 2.6, baseY, 0.19);
    winglet(elements, side, [8 + side * span, 5.6], [8 + side * span, 8.2], 1.7, baseY, 0.19);

    const fin = [8 + Math.min(side * 0.32, side * 1.5), 8 + Math.max(side * 0.32, side * 1.5)];
    elements.push(cube([fin[0], 8.9, 11.6], [fin[1], 12.3, 13.4], "wing",
      rot("z", side * 45, [8, 9.0, 12.5])));
    elements.push(cube([fin[0] + 0.03, 8.9, 13.0], [fin[1] - 0.03, 12.3, 13.5], "accent",
      rot("z", side * 45, [8, 9.0, 12.5])));
  }

  elements.push(cube([7.5, 7.9, 12.9], [8.5, 8.6, 13.6], "metal"));
  pusherProp(elements, 13.6, 3.1);

  return model(planeTextures({
    body: "minecraft:block/gray_concrete",
    wing: "minecraft:block/gray_concrete",
    accent: "minecraft:block/red_concrete",
    canopy: "minecraft:block/black_stained_glass",
    nose: "minecraft:block/black_concrete",
    warhead: "minecraft:block/black_concrete"
  }), elements, planeDisplay);
}

function planeInterceptorModel() {
  const elements = [];
  const span = 5.6;

  elements.push(cube([7.34, 7.44, 3.3], [8.66, 8.68, 11.2], "body"));
  elements.push(cube([7.52, 7.6, 2.5], [8.48, 8.52, 3.3], "nose"));
  elements.push(cube([7.42, 8.68, 4.2], [8.58, 9.1, 6.4], "canopy"));
  elements.push(cube([7.46, 7.16, 4.6], [8.54, 7.5, 8.6], "dark"));

  for (const side of [-1, 1]) {
    straightPanel(elements, side, 0.4, 2.8, 5.4, 2.3, 8.05, 0.19);
    straightPanel(elements, side, 2.8, span, 5.9, 1.8, 8.05, 0.14);
    winglet(elements, side, [8 + side * span, 5.9], [8 + side * span, 7.7], 1.4, 8.05, 0.14);

    const stab = [8 + Math.min(side * 0.4, side * 3.0), 8 + Math.max(side * 0.4, side * 3.0)];
    elements.push(cube([stab[0], 7.92, 10.2], [stab[1], 8.18, 11.9], "wing"));
    elements.push(cube([stab[0], 7.95, 11.6], [stab[1], 8.15, 12.3], "accent"));
  }

  elements.push(cube([7.86, 8.18, 10.2], [8.14, 11.0, 11.9], "wing"));
  elements.push(cube([7.88, 8.18, 11.6], [8.12, 11.0, 12.4], "accent"));
  elements.push(cube([7.88, 6.0, 10.4], [8.12, 7.92, 11.7], "wing"));

  tractorProp(elements, 2.5, 2.6);

  return model(planeTextures({
    body: "minecraft:block/black_concrete",
    wing: "minecraft:block/black_concrete",
    accent: "minecraft:block/lime_concrete",
    canopy: "minecraft:block/lime_stained_glass",
    nose: "minecraft:block/lime_concrete"
  }), elements, planeDisplay);
}

function planeCarrierModel() {
  const elements = [];
  const span = 8.8;
  const leadZ = 4.4;
  const chord = 4.0;
  const baseY = 9.6;

  elements.push(cube([6.1, 6.2, 2.9], [9.9, 9.6, 11.2], "body"));
  elements.push(cube([6.5, 6.6, 1.6], [9.5, 9.2, 2.9], "nose"));
  elements.push(cube([7.0, 7.0, 0.85], [9.0, 8.8, 1.6], "nose"));
  elements.push(cube([6.3, 9.6, 3.1], [9.7, 10.05, 4.4], "canopy"));
  elements.push(cube([6.2, 9.52, 8.4], [9.8, 9.86, 11.0], "dark"));

  elements.push(cube([6.2, 5.9, 4.2], [6.62, 6.3, 10.4], "dark"));
  elements.push(cube([9.38, 5.9, 4.2], [9.8, 6.3, 10.4], "dark"));
  elements.push(cube([6.62, 6.06, 4.2], [9.38, 6.28, 4.7], "dark"));
  elements.push(cube([6.62, 6.06, 9.9], [9.38, 6.28, 10.4], "dark"));
  elements.push(cube([6.34, 5.5, 4.5], [6.62, 6.06, 10.1], "accent"));
  elements.push(cube([9.38, 5.5, 4.5], [9.66, 6.06, 10.1], "accent"));

  for (const side of [-1, 1]) {
    straightPanel(elements, side, 0.0, 3.9, leadZ, chord, baseY, 0.3);
    straightPanel(elements, side, 3.9, 6.4, leadZ, chord - 0.5, baseY, 0.26);
    straightPanel(elements, side, 6.4, span, leadZ, chord - 1.1, baseY, 0.2);
    winglet(elements, side, [8 + side * span, leadZ], [8 + side * span, leadZ + chord - 1.1], 1.8, baseY, 0.2);

    const boom = [8 + Math.min(side * 3.0, side * 4.1), 8 + Math.max(side * 3.0, side * 4.1)];
    elements.push(cube([boom[0], 8.9, leadZ - 0.4], [boom[1], baseY + 0.34, 14.6], "body"));
    elements.push(cube([boom[0] + 0.02, 8.96, 11.2], [boom[1] - 0.02, baseY + 0.28, 14.58], "wing"));
    elements.push(cube([boom[0] + 0.08, 9.9, 13.2], [boom[1] - 0.08, 13.1, 14.66], "wing"));
    elements.push(cube([boom[0] + 0.16, 13.1, 13.4], [boom[1] - 0.16, 13.42, 14.74], "wing"));
    elements.push(cube([boom[0] + 0.14, 12.3, 14.2], [boom[1] - 0.14, 13.06, 14.72], "accent"));
    elements.push(cube([boom[0] + 0.1, 8.3, leadZ + 0.9], [boom[1] - 0.1, 8.94, leadZ + 2.8], "dark"));

    for (const station of [5.0, 6.9]) {
      const px = [8 + Math.min(side * (station - 0.24), side * (station + 0.24)),
                  8 + Math.max(side * (station - 0.24), side * (station + 0.24))];
      elements.push(cube([px[0], 8.7, leadZ + 0.9], [px[1], baseY - 0.2, leadZ + 2.3], "metal"));
      elements.push(cube([px[0] - 0.32, 7.9, leadZ + 0.5], [px[1] + 0.32, 8.72, leadZ + 3.1], "warhead"));
      elements.push(cube([px[0] - 0.18, 8.0, leadZ + 3.1], [px[1] + 0.18, 8.6, leadZ + 3.5], "accent"));
    }
  }

  elements.push(cube([4.0, 13.42, 13.4], [12.0, 13.8, 14.62], "wing"));
  elements.push(cube([4.0, 13.45, 14.15], [12.0, 13.77, 15.0], "accent"));

  pusherPropAt(elements, 11.3, 2.6, 8, 8.0);

  return model(planeTextures({
    body: "minecraft:block/green_concrete",
    wing: "minecraft:block/green_concrete",
    accent: "minecraft:block/orange_concrete",
    canopy: "minecraft:block/black_stained_glass",
    nose: "minecraft:block/gray_concrete",
    metal: "minecraft:block/iron_block",
    warhead: "minecraft:block/gray_concrete"
  }), elements, planeDisplay);
}


function planeModel(kind) {
  const scout = kind === "plane_scout";
  const kamikaze = kind === "plane_kamikaze";
  const span = scout ? 7.7 : kamikaze ? 6.3 : 7.0;
  const elements = [];

  // Centre pod: a moulded EPP fuselage tapering into a nose cone, with the canopy hatch
  // the battery drops through on top.
  elements.push(cube([7.02, 7.18, 4.4], [8.98, 8.98, 10.2], "body"));
  elements.push(cube([7.22, 7.34, 3.3], [8.78, 8.78, 4.4], "body"));
  elements.push(cube([7.46, 7.54, 2.45], [8.54, 8.5, 3.3], "nose"));
  elements.push(cube([7.68, 7.72, 1.85], [8.32, 8.3, 2.45], "nose"));
  elements.push(cube([7.3, 8.98, 5.1], [8.7, 9.42, 8.9], "canopy"));
  elements.push(cube([7.14, 7.06, 5.4], [8.86, 7.24, 9.4], "dark"));

  // Swept wing panels. Every strip of a panel turns about the same root pivot and shares
  // one leading edge, so the sweep stays straight and only the trailing edge tapers.
  const leadZ = 5.25;
  const pivotZ = 7.2;
  const sweep = 22.5;
  const chordAt = f => 4.45 - 2.5 * f;
  const stations = [-1.0, 2.0, 3.7, 5.3, span];

  // Unrotated centre section. Both swept panels pivot away from the centreline, which
  // otherwise leaves an open wedge behind the fuselage.
  const rootChord = chordAt(0);
  elements.push(cube([6.42, 7.72, leadZ - 0.05], [9.58, 8.28, leadZ + rootChord + 0.55], "wing"));
  elements.push(cube([6.46, 7.76, leadZ - 0.22], [9.54, 8.24, leadZ + 0.08], "dark"));
  elements.push(cube([6.5, 7.79, leadZ + rootChord + 0.1], [9.5, 8.21, leadZ + rootChord + 0.62], "accent"));

  for (const side of [-1, 1]) {
    const angle = -side * sweep;
    const turn = rot("y", angle, [8, 8, pivotZ]);
    const strip = (inner, outer, z1, z2, y1, y2, texture) => {
      const a = 8 + Math.min(side * inner, side * outer);
      const b = 8 + Math.max(side * inner, side * outer);
      elements.push(cube([a, y1, z1], [b, y2, z2], texture, turn));
    };
    for (let i = 0; i < stations.length - 1; i++) {
      const inner = stations[i];
      const outer = stations[i + 1];
      const f = Math.max(0, (inner + outer) / 2) / span;
      const chord = chordAt(f);
      const half = 0.25 - 0.12 * f;
      strip(inner, outer, leadZ, leadZ + chord, 8 - half, 8 + half, "wing");
      strip(inner, outer, leadZ - 0.16, leadZ + 0.05, 8 - half + 0.01, 8 + half - 0.01, "dark");
      if (i > 0) {
        strip(inner, outer, leadZ + chord - 0.06, leadZ + chord + 0.5, 8 - half + 0.02, 8 + half - 0.02, "accent");
      }
    }
    const tipChord = chordAt(1);
    const [lx, lz] = rotY(side * span, leadZ - pivotZ, angle);
    const [tx, tz] = rotY(side * span, leadZ + tipChord - pivotZ, angle);
    winglet(elements, side, [8 + lx, pivotZ + lz], [8 + tx, pivotZ + tz], 1.55, 8, 0.13);
  }

  pusherProp(elements, 11.0);

  if (scout) {
    // Gimbal ball slung under the pod.
    elements.push(cube([7.24, 6.5, 5.5], [8.76, 7.1, 7.1], "dark"));
    elements.push(cube([7.24, 6.5, 5.5], [8.76, 7.1, 7.1], "dark", rot("y", 45, [8, 6.8, 6.3])));
    elements.push(cube([7.6, 6.62, 5.05], [8.4, 7.02, 5.55], "lens"));
    elements.push(cube([6.9, 9.42, 6.0], [7.1, 11.6, 6.4], "dark", rot("z", 22.5, [7, 9.42, 6.2])));
  }
  if (kamikaze) {
    elements.push(cube([7.06, 6.42, 5.2], [8.94, 7.22, 9.0], "warhead"));
    elements.push(cube([7.02, 6.62, 5.9], [8.98, 7.02, 6.5], "yellow"));
    elements.push(cube([7.02, 6.62, 7.7], [8.98, 7.02, 8.3], "yellow"));
    elements.push(cube([7.56, 7.6, 1.35], [8.44, 8.42, 1.9], "warhead"));
  }

  return model(planeTextures({
    wing: scout ? "minecraft:block/light_gray_concrete" : kamikaze ? "minecraft:block/green_concrete" : "minecraft:block/white_concrete",
    accent: scout ? "minecraft:block/light_blue_concrete" : "minecraft:block/red_concrete",
    canopy: scout ? "minecraft:block/light_blue_stained_glass" : "minecraft:block/black_concrete",
    nose: kamikaze ? "minecraft:block/red_concrete" : "minecraft:block/orange_concrete"
  }), elements, planeDisplay);
}

function planeDeltaModel() {
  const elements = [];
  const span = 7.3;
  const root = 1.05;
  const trailZ = 11.4;
  const steps = 10;
  const leadOf = f => 2.9 + f * 6.4;

  elements.push(cube([6.9, 7.24, 3.4], [9.1, 9.2, 11.4], "body"));
  elements.push(cube([7.24, 7.5, 2.1], [8.76, 8.72, 3.4], "nose"));
  elements.push(cube([7.54, 7.72, 1.25], [8.46, 8.42, 2.1], "nose"));
  elements.push(cube([7.2, 9.2, 4.3], [8.8, 9.72, 7.9], "canopy"));
  elements.push(cube([7.0, 7.06, 4.6], [9.0, 7.28, 10.6], "dark"));
  elements.push(cube([6.4, 7.5, 8.2], [7.0, 8.9, 10.9], "dark"));
  elements.push(cube([9.0, 7.5, 8.2], [9.6, 8.9, 10.9], "dark"));

  for (const side of [-1, 1]) {
    for (let i = 0; i < steps; i++) {
      const f0 = i / steps;
      const f1 = (i + 1) / steps;
      const fm = (f0 + f1) / 2;
      const x0 = root + f0 * (span - root);
      const x1 = root + f1 * (span - root);
      const lead = leadOf(fm);
      const half = 0.36 - 0.22 * fm;
      const a = 8 + Math.min(side * x0, side * x1);
      const b = 8 + Math.max(side * x0, side * x1);
      elements.push(cube([a, 8 - half, lead], [b, 8 + half, trailZ], "wing"));
      elements.push(cube([a, 8 - half + 0.03, lead - 0.26], [b, 8 + half - 0.03, lead + 0.08], "dark"));
      if (i > 2) {
        elements.push(cube([a, 8 - half + 0.03, trailZ - 0.06], [b, 8 + half - 0.03, trailZ + 0.55], "accent"));
      }
    }
    const tipLead = leadOf((steps - 0.5) / steps);
    winglet(elements, side, [8 + side * span, tipLead], [8 + side * span, trailZ], 2.1, 8, 0.15);
  }

  pusherProp(elements, 11.9, 3.0);

  return model(planeTextures({
    wing: "minecraft:block/gray_concrete",
    accent: "minecraft:block/orange_concrete",
    canopy: "minecraft:block/black_stained_glass",
    nose: "minecraft:block/orange_concrete"
  }), elements, planeDisplay);
}

function planeGliderModel() {
  const elements = [];

  elements.push(cube([7.06, 6.98, 3.5], [8.94, 9.04, 8.7], "body"));
  elements.push(cube([7.28, 7.28, 2.4], [8.72, 8.82, 3.5], "nose"));
  elements.push(cube([7.5, 7.54, 1.75], [8.5, 8.6, 2.4], "nose"));
  elements.push(cube([7.24, 9.04, 3.7], [8.76, 9.5, 5.15], "canopy"));
  elements.push(cube([7.34, 7.5, 8.5], [8.66, 8.9, 12.9], "body"));
  elements.push(cube([7.44, 7.62, 12.9], [8.56, 8.78, 14.25], "body"));

  elements.push(cube([7.62, 8.6, 12.35], [8.38, 11.5, 14.2], "wing"));
  elements.push(cube([7.68, 11.5, 12.5], [8.32, 11.72, 14.2], "wing"));
  elements.push(cube([4.5, 11.72, 12.6], [11.5, 12.04, 14.3], "wing"));
  elements.push(cube([4.5, 11.75, 14.0], [11.5, 12.01, 14.62], "accent"));
  elements.push(cube([7.7, 11.06, 13.5], [8.3, 11.54, 14.2], "accent"));

  const span = 7.85;
  const leadZ = 4.25;
  const baseY = 9.62;
  const panels = [[0.0, 3.0, 3.1], [3.0, 5.7, 2.6], [5.7, span, 2.05]];
  elements.push(cube([7.06, 8.9, 5.2], [8.94, baseY - 0.14, 7.4], "body"));
  for (const side of [-1, 1]) {
    for (const [x0, x1, chord] of panels) {
      const a = 8 + Math.min(side * x0, side * x1);
      const b = 8 + Math.max(side * x0, side * x1);
      elements.push(cube([a, baseY - 0.2, leadZ], [b, baseY + 0.2, leadZ + chord], "wing"));
      elements.push(cube([a, baseY - 0.17, leadZ - 0.18], [b, baseY + 0.17, leadZ + 0.06], "dark"));
      elements.push(cube([a, baseY - 0.15, leadZ + chord - 0.06], [b, baseY + 0.15, leadZ + chord + 0.42], "accent"));
    }
    const tipChord = panels[panels.length - 1][2];
    winglet(elements, side, [8 + side * span, leadZ], [8 + side * span, leadZ + tipChord], 1.65, baseY, 0.2);
    elements.push(cube([8 + Math.min(side * 1.3, side * 2.3), 8.9, 5.0],
      [8 + Math.max(side * 1.3, side * 2.3), baseY - 0.16, 6.6], "metal", rot("z", side * 22.5, [8, 9.3, 5.8])));
  }

  tractorProp(elements, 1.75, 3.1);

  return model(planeTextures({
    wing: "minecraft:block/white_concrete",
    accent: "minecraft:block/light_blue_concrete",
    canopy: "minecraft:block/light_blue_stained_glass",
    nose: "minecraft:block/light_blue_concrete"
  }), elements, planeDisplay);
}

function planeCargoModel() {
  const elements = [];

  elements.push(cube([6.4, 6.4, 3.3], [9.6, 9.4, 10.5], "body"));
  elements.push(cube([6.7, 6.7, 2.2], [9.3, 9.1, 3.3], "nose"));
  elements.push(cube([7.1, 7.0, 1.5], [8.9, 8.7, 2.2], "nose"));
  elements.push(cube([6.6, 9.4, 3.5], [9.4, 9.78, 4.55], "canopy"));
  elements.push(cube([6.5, 9.34, 8.4], [9.5, 9.62, 10.4], "dark"));
  const bayZ0 = 4.3;
  const bayZ1 = 9.9;
  elements.push(cube([6.3, 5.4, bayZ0], [6.66, 6.5, bayZ1], "body"));
  elements.push(cube([9.34, 5.4, bayZ0], [9.7, 6.5, bayZ1], "body"));
  elements.push(cube([6.66, 5.4, bayZ0], [9.34, 6.5, bayZ0 + 0.55], "body"));
  elements.push(cube([6.66, 5.4, bayZ1 - 0.55], [9.34, 6.5, bayZ1], "body"));
  elements.push(cube([6.66, 6.26, bayZ0 + 0.55], [9.34, 6.5, bayZ1 - 0.55], "dark"));
  elements.push(cube([6.9, 5.52, bayZ0 + 1.0], [8.02, 6.28, bayZ0 + 2.2], "crate"));
  elements.push(cube([8.1, 5.7, bayZ0 + 1.3], [9.14, 6.28, bayZ0 + 2.4], "crate_alt"));
  elements.push(cube([7.05, 5.46, bayZ0 + 3.1], [8.85, 6.28, bayZ0 + 4.5], "crate"));
  elements.push(cube([6.2, 5.6, bayZ0 + 0.3], [6.32, 6.4, bayZ1 - 0.3], "accent"));
  elements.push(cube([9.68, 5.6, bayZ0 + 0.3], [9.8, 6.4, bayZ1 - 0.3], "accent"));
  elements.push(cube([6.66, 5.16, bayZ0 + 0.65], [8.02, 5.4, bayZ1 - 0.65], "wing",
    rot("z", -45, [6.7, 5.4, 7.1])));
  elements.push(cube([7.98, 5.16, bayZ0 + 0.65], [9.34, 5.4, bayZ1 - 0.65], "wing",
    rot("z", 45, [9.3, 5.4, 7.1])));
  elements.push(cube([6.42, 6.6, 4.5], [6.66, 8.7, 9.4], "accent"));
  elements.push(cube([9.34, 6.6, 4.5], [9.58, 8.7, 9.4], "accent"));
  elements.push(cube([7.02, 6.9, 2.05], [8.98, 8.5, 2.3], "canopy"));

  const span = 7.55;
  const leadZ = 4.6;
  const chord = 3.6;
  const baseY = 9.35;
  const panels = [[0.0, 3.5], [3.5, 5.7], [5.7, span]];
  for (const side of [-1, 1]) {
    for (const [x0, x1] of panels) {
      const a = 8 + Math.min(side * x0, side * x1);
      const b = 8 + Math.max(side * x0, side * x1);
      elements.push(cube([a, baseY - 0.24, leadZ], [b, baseY + 0.24, leadZ + chord], "wing"));
      elements.push(cube([a, baseY - 0.2, leadZ - 0.2], [b, baseY + 0.2, leadZ + 0.06], "dark"));
      elements.push(cube([a, baseY - 0.18, leadZ + chord - 0.06], [b, baseY + 0.18, leadZ + chord + 0.5], "accent"));
    }
    winglet(elements, side, [8 + side * span, leadZ], [8 + side * span, leadZ + chord], 1.5, baseY, 0.24);

    const boom = [8 + Math.min(side * 2.7, side * 3.68), 8 + Math.max(side * 2.7, side * 3.68)];
    elements.push(cube([boom[0], 8.66, leadZ - 0.3], [boom[1], baseY + 0.3, 14.3], "body"));
    elements.push(cube([boom[0] + 0.02, 8.72, 11.0], [boom[1] - 0.02, baseY + 0.24, 14.28], "wing"));
    elements.push(cube([boom[0] + 0.08, 9.4, 13.1], [boom[1] - 0.08, 12.5, 14.36], "wing"));
    elements.push(cube([boom[0] + 0.16, 12.5, 13.28], [boom[1] - 0.16, 12.8, 14.44], "wing"));
    elements.push(cube([boom[0] + 0.14, 11.72, 13.94], [boom[1] - 0.14, 12.46, 14.42], "accent"));
    elements.push(cube([boom[0] + 0.12, 8.1, leadZ + 0.9], [boom[1] - 0.12, 8.7, leadZ + 2.6], "dark"));
  }
  elements.push(cube([4.6, 12.06, 13.3], [11.4, 12.42, 14.35], "wing"));
  elements.push(cube([4.6, 12.09, 14.05], [11.4, 12.39, 14.75], "accent"));

  pusherProp(elements, 10.6, 2.35);

  return model(planeTextures({
    wing: "minecraft:block/yellow_concrete",
    accent: "minecraft:block/gray_concrete",
    canopy: "minecraft:block/black_stained_glass",
    nose: "minecraft:block/gray_concrete",
    crate: "minecraft:block/barrel_side",
    crate_alt: "minecraft:block/oak_planks"
  }), elements, planeDisplay);
}

write("plane", planeModel("plane"));
write("plane_scout", planeModel("plane_scout"));
write("plane_kamikaze", planeModel("plane_kamikaze"));
write("plane_delta", planeDeltaModel());
write("plane_glider", planeGliderModel());
write("plane_cargo", planeCargoModel());
write("plane_carrier", planeCarrierModel());
write("plane_shahed", planeShahedModel());
write("plane_longrange", planeLongRangeModel());
write("plane_interceptor", planeInterceptorModel());

// Wing props and their motors are modelled nose-forward: the disc sits at -Z of the mount
// and the can behind it. A pusher installation is the same parts turned around, which the
// renderer does by yawing the pair 180 degrees.
const wingPropDisplay = { gui: { rotation: [20, 215, 0], scale: [1.05, 1.05, 1.05] } };

function planeMotorModel(name, half, len) {
  const z2 = 8.0 + len;
  const elements = [];
  const can = (a, b, h, tex) => {
    elements.push(cube([8 - h, 8 - h, a], [8 + h, 8 + h, b], tex));
    elements.push(cube([8 - h, 8 - h, a], [8 + h, 8 + h, b], tex, rot("z", 45, [8, 8, (a + b) / 2])));
  };
  can(8.0, 8.0 + len * 0.14, half * 0.6, "metal");
  can(8.0 + len * 0.14, 8.0 + len * 0.72, half, "main");
  can(8.0 + len * 0.72, z2 - 0.26, half * 0.88, "dark");
  can(z2 - 0.26, z2, half * 1.15, "mount");
  elements.push(cube([8 - half * 0.2, 8 - half * 0.2, 7.2], [8 + half * 0.2, 8 + half * 0.2, 8.0], "metal"));
  const arm = half * 1.5;
  elements.push(cube([8 - arm, 7.82, z2 - 0.02], [8 + arm, 8.18, z2 + 0.34], "mount"));
  elements.push(cube([7.82, 8 - arm, z2 - 0.02], [8.18, 8 + arm, z2 + 0.34], "mount"));
  elements.push(cube([8 - 0.5, 8 - half - 0.42, 8.0 + len * 0.3], [8 + 0.5, 8 - half + 0.08, 8.0 + len * 0.62], "wire"));
  return model({
    main: "fpv:item/motor_2306_1700kv",
    dark: "minecraft:block/black_concrete",
    metal: "minecraft:block/iron_block",
    mount: "minecraft:block/gray_concrete",
    wire: "minecraft:block/red_concrete"
  }, elements, wingPropDisplay);
}

write("motor_2814_1100kv", planeMotorModel("motor_2814_1100kv", 0.9, 1.3));
write("motor_3548_900kv", planeMotorModel("motor_3548_900kv", 1.15, 1.65));
write("motor_4250_650kv", planeMotorModel("motor_4250_650kv", 1.42, 2.05));

function planePropModel(name, radius, blade) {
  const elements = [];
  spinnerBlades(elements, 7.6, radius, true, 8);
  elements.push(cube([7.48, 7.48, 7.14], [8.52, 8.52, 7.66], "nose"));
  elements.push(cube([7.48, 7.48, 7.14], [8.52, 8.52, 7.66], "nose", rot("z", 45, [8, 8, 7.4])));
  elements.push(cube([7.7, 7.7, 6.72], [8.3, 8.3, 7.18], "nose"));
  elements.push(cube([7.85, 7.85, 6.42], [8.15, 8.15, 6.76], "nose"));
  return model({ prop: blade, nose: "minecraft:block/light_gray_concrete" }, elements, wingPropDisplay);
}

write("prop_8x6", planePropModel("prop_8x6", 3.4, "minecraft:block/white_concrete"));
write("prop_10x5", planePropModel("prop_10x5", 4.2, "minecraft:block/gray_concrete"));
write("prop_12x6", planePropModel("prop_12x6", 5.0, "minecraft:block/black_concrete"));

write("plane_frame_trainer", airframe(() => planeModel("plane")));
write("plane_frame_delta", airframe(planeDeltaModel));
write("plane_frame_glider", airframe(planeGliderModel));
write("plane_frame_strike", airframe(planeLongRangeModel));
write("plane_frame_cargo", airframe(planeCargoModel));
write("plane_frame_wing", airframe(planeInterceptorModel));
write("plane_frame_scout", airframe(() => planeModel("plane_scout")));
write("plane_frame_kamikaze", airframe(() => planeModel("plane_kamikaze")));
write("plane_frame_carrier", airframe(planeCarrierModel));
write("plane_frame_loiter", airframe(planeShahedModel));

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

const WARHEAD_SPECS = {
  frag:    { r: 1.55, z0: 3.6, z1: 11.0, body: "minecraft:block/green_concrete",
             band: "minecraft:block/yellow_concrete", ribs: true },
  he:      { r: 1.95, z0: 2.8, z1: 11.8, body: "minecraft:block/green_concrete",
             band: "minecraft:block/yellow_concrete" },
  heavy:   { r: 2.65, z0: 1.6, z1: 13.2, body: "minecraft:block/gray_concrete",
             band: "minecraft:block/red_concrete" },
  shaped:  { r: 1.75, z0: 2.6, z1: 11.2, body: "minecraft:block/black_concrete",
             band: "minecraft:block/copper_block", shaped: true },
  thermo:  { r: 2.35, z0: 2.2, z1: 12.6, body: "minecraft:block/red_concrete",
             band: "minecraft:block/orange_concrete" },
  cluster: { r: 2.25, z0: 2.6, z1: 12.2, body: "minecraft:block/light_gray_concrete",
             band: "minecraft:block/lime_concrete", cluster: true },
  proximity: { r: 1.8, z0: 2.4, z1: 11.4, body: "minecraft:block/light_blue_concrete",
               band: "minecraft:block/yellow_concrete", proximity: true }
};

const warheadDisplay = {
  gui: { rotation: [30, 145, 0], translation: [0, 0.5, 0], scale: [0.95, 0.95, 0.95] }
};

function warheadModel(id) {
  const s = WARHEAD_SPECS[id];
  const elements = [];
  const ring = (y0, y1, half, tex) => elements.push(cube([8 - half, 8 - half, y0], [8 + half, 8 + half, y1], tex));

  const noseLen = s.r * 1.5;
  for (let i = 0; i < 4; i++) {
    const half = s.r * (0.3 + 0.7 * ((i + 1) / 4));
    ring(s.z0 + (noseLen * i) / 4, s.z0 + (noseLen * (i + 1)) / 4 + 0.02, half, s.shaped ? "liner" : "body");
  }
  ring(s.z0 + noseLen, s.z1 - s.r * 1.2, s.r, "body");

  for (let i = 0; i < 3; i++) {
    const half = s.r * (1 - 0.22 * (i + 1));
    ring(s.z1 - (s.r * 1.2 * (3 - i)) / 3, s.z1 - (s.r * 1.2 * (2 - i)) / 3 + 0.02, half, "body");
  }

  ring(s.z0 + noseLen + 0.5, s.z0 + noseLen + 1.0, s.r + 0.07, "band");
  ring(s.z1 - s.r * 1.2 - 1.0, s.z1 - s.r * 1.2 - 0.5, s.r + 0.07, "band");

  if (s.ribs) {
    for (const f of [0.32, 0.5, 0.68]) {
      const z = s.z0 + noseLen + (s.z1 - s.r * 1.2 - s.z0 - noseLen) * f;
      ring(z, z + 0.3, s.r + 0.12, "dark");
    }
  }
  if (s.shaped) {
    ring(s.z0 - 0.5, s.z0 + 0.02, s.r * 0.28, "band");
    ring(s.z0 + noseLen - 0.3, s.z0 + noseLen + 0.6, s.r * 0.8, "dark");
  }
  if (s.cluster) {
    const mid = (s.z0 + noseLen + s.z1 - s.r * 1.2) / 2;
    for (const dz of [-1.6, 0, 1.6]) {
      elements.push(cube([8 - s.r - 0.1, 8 - 0.55, mid + dz - 0.5], [8 - s.r + 0.16, 8 + 0.55, mid + dz + 0.5], "dark"));
      elements.push(cube([8 + s.r - 0.16, 8 - 0.55, mid + dz - 0.5], [8 + s.r + 0.1, 8 + 0.55, mid + dz + 0.5], "dark"));
    }
  }

  if (s.proximity) {
    const wz = s.z0 + noseLen * 0.7;
    ring(s.z0 - 0.4, s.z0 + 0.04, s.r * 0.34, "band");
    elements.push(cube([8 - s.r - 1.6, 7.88, wz - 0.12], [8 - s.r + 0.25, 8.12, wz + 0.12], "metal"));
    elements.push(cube([8 + s.r - 0.25, 7.88, wz - 0.12], [8 + s.r + 1.6, 8.12, wz + 0.12], "metal"));
    elements.push(cube([7.88, 8 - s.r - 1.6, wz - 0.12], [8.12, 8 - s.r + 0.25, wz + 0.12], "metal"));
    elements.push(cube([7.88, 8 + s.r - 0.25, wz - 0.12], [8.12, 8 + s.r + 1.6, wz + 0.12], "metal"));
    ring(wz + 1.4, wz + 1.9, s.r + 0.09, "band");
  }

  const fin = s.r + 0.95;
  const fz0 = s.z1 - s.r * 1.2 - 0.4;
  const fz1 = s.z1 + 0.35;
  elements.push(cube([7.88, 8 - fin, fz0], [8.12, 8 + fin, fz1], "fin"));
  elements.push(cube([8 - fin, 7.88, fz0], [8 + fin, 8.12, fz1], "fin"));
  elements.push(cube([8 - fin + 0.1, 7.9, fz1 - 0.55], [8 + fin - 0.1, 8.1, fz1 + 0.3], "band"));
  elements.push(cube([7.9, 8 - fin + 0.1, fz1 - 0.55], [8.1, 8 + fin - 0.1, fz1 + 0.3], "band"));

  const lugZ = (s.z0 + noseLen + s.z1 - s.r * 1.2) / 2;
  for (const dz of [-1.1, 1.1]) {
    elements.push(cube([7.7, 8 + s.r - 0.05, lugZ + dz - 0.28], [8.3, 8 + s.r + 0.62, lugZ + dz + 0.28], "metal"));
  }

  return model({
    body: s.body,
    band: s.band,
    liner: s.shaped ? "minecraft:block/copper_block" : s.body,
    dark: "minecraft:block/black_concrete",
    fin: "minecraft:block/gray_concrete",
    metal: "minecraft:block/iron_block"
  }, elements, warheadDisplay);
}

for (const id of Object.keys(WARHEAD_SPECS)) write("warhead_" + id, warheadModel(id));

const radioDisplay = {
  gui: { rotation: [18, -32, 0], translation: [0, -1, 0], scale: [0.86, 0.86, 0.86] },
  ground: { translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
  fixed: { rotation: [0, 0, 0], translation: [0, -2, 0], scale: [0.8, 0.8, 0.8] },
  thirdperson_righthand: { rotation: [15, 0, 0], translation: [0, 3.5, 1], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { rotation: [10, -10, 0], translation: [1, 3.5, 2], scale: [0.62, 0.62, 0.62] }
};

function transmitterModel(longRange) {
  const elements = [];
  const accent = longRange ? "gold" : "accent";

  elements.push(cube([3.6, 3.4, 6.6], [12.4, 12.2, 9.4], "shell"));
  elements.push(cube([3.2, 4.2, 7.0], [3.6, 11.4, 9.0], "shell"));
  elements.push(cube([12.4, 4.2, 7.0], [12.8, 11.4, 9.0], "shell"));
  elements.push(cube([3.9, 3.7, 6.3], [12.1, 11.9, 6.6], "dark"));

  elements.push(cube([5.1, 8.1, 6.0], [10.9, 11.5, 6.35], "frame"));
  elements.push(cube([5.4, 8.4, 5.85], [10.6, 11.2, 6.05], "screen"));

  for (const x of [4.6, 9.3]) {
    elements.push(cube([x, 4.4, 6.05], [x + 2.1, 6.5, 6.65], "frame"));
    elements.push(cube([x + 0.35, 4.75, 5.75], [x + 1.75, 6.15, 6.1], "dark"));
    elements.push(cube([x + 0.85, 5.25, 4.3], [x + 1.25, 5.65, 5.85], "metal"));
    elements.push(cube([x + 0.6, 5.0, 3.6], [x + 1.5, 5.9, 4.5], accent));
  }

  for (const x of [4.3, 5.6, 10.0, 11.3]) {
    elements.push(cube([x, 12.2, 7.4], [x + 0.7, 13.6, 8.1], "metal"));
    elements.push(cube([x - 0.15, 13.6, 7.25], [x + 0.85, 14.0, 8.25], accent));
  }
  elements.push(cube([7.2, 12.2, 7.3], [8.8, 12.7, 8.7], "frame"));

  elements.push(cube([7.4, 12.7, 7.6], [8.6, 15.2, 8.4], "dark"));
  elements.push(cube([7.55, 15.2, 7.75], [8.45, 16.0, 8.25], "metal"));
  if (longRange) {
    elements.push(cube([4.6, 12.2, 7.6], [5.4, 15.6, 8.4], "dark", rot("z", 22.5, [5, 12.4, 8])));
    elements.push(cube([10.6, 12.2, 7.6], [11.4, 15.6, 8.4], "dark", rot("z", -22.5, [11, 12.4, 8])));
    elements.push(cube([3.6, 6.6, 6.4], [12.4, 7.4, 6.7], "gold"));
  }

  elements.push(cube([3.2, 0.6, 6.4], [6.0, 3.5, 9.6], "grip", rot("z", 22.5, [4.6, 3.4, 8])));
  elements.push(cube([10.0, 0.6, 6.4], [12.8, 3.5, 9.6], "grip", rot("z", -22.5, [11.4, 3.4, 8])));
  elements.push(cube([6.0, 2.4, 6.9], [10.0, 3.5, 9.4], "shell"));
  elements.push(cube([7.1, 1.9, 7.3], [8.9, 2.5, 9.0], "dark"));

  elements.push(cube([4.2, 9.6, 9.4], [11.8, 11.2, 9.9], "grip"));
  elements.push(cube([7.4, 11.2, 9.5], [8.6, 12.6, 9.85], "metal"));

  return model({
    particle: "minecraft:block/polished_blackstone",
    shell: "minecraft:block/polished_blackstone",
    frame: "minecraft:block/deepslate_tiles",
    dark: "minecraft:block/black_concrete",
    screen: longRange ? "minecraft:block/light_blue_concrete" : "minecraft:block/lime_concrete",
    metal: "minecraft:block/iron_block",
    grip: "minecraft:block/black_wool",
    accent: "minecraft:block/red_concrete",
    gold: "minecraft:block/gold_block"
  }, elements, radioDisplay);
}

const gogglesDisplay = {
  gui: { rotation: [22, -140, 0], translation: [0, -1.5, 0], scale: [0.9, 0.9, 0.9] },
  ground: { translation: [0, 3, 0], scale: [0.4, 0.4, 0.4] },
  fixed: { rotation: [0, 180, 0], scale: [0.8, 0.8, 0.8] },
  thirdperson_righthand: { rotation: [60, 40, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
  firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 2.5, 0], scale: [0.6, 0.6, 0.6] },
  head: { rotation: [0, 0, 0], translation: [0, -1, -4], scale: [1.1, 1.1, 1.1] }
};

function gogglesModel() {
  const elements = [];

  elements.push(cube([3.0, 5.6, 5.4], [13.0, 10.4, 9.4], "shell"));
  elements.push(cube([3.4, 6.0, 4.9], [12.6, 10.0, 5.4], "frame"));
  elements.push(cube([4.0, 6.6, 4.6], [7.4, 9.4, 4.95], "lens"));
  elements.push(cube([8.6, 6.6, 4.6], [12.0, 9.4, 4.95], "lens"));
  elements.push(cube([7.4, 7.0, 5.0], [8.6, 9.0, 5.45], "dark"));

  for (const x of [4.4, 8.9]) {
    elements.push(cube([x, 6.5, 9.4], [x + 2.7, 9.5, 10.6], "foam"));
    elements.push(cube([x + 0.3, 6.8, 10.6], [x + 2.4, 9.2, 11.1], "foam"));
  }

  elements.push(cube([4.6, 10.4, 6.4], [5.6, 13.4, 7.4], "dark", rot("z", 22.5, [5.1, 10.6, 6.9])));
  elements.push(cube([10.4, 10.4, 6.4], [11.4, 13.4, 7.4], "dark", rot("z", -22.5, [10.9, 10.6, 6.9])));
  elements.push(cube([7.0, 10.4, 6.6], [9.0, 11.0, 8.8], "frame"));
  elements.push(cube([7.4, 11.0, 7.0], [8.6, 12.0, 8.4], "metal"));

  elements.push(cube([2.4, 6.8, 6.2], [3.0, 9.2, 9.0], "frame"));
  elements.push(cube([13.0, 6.8, 6.2], [13.6, 9.2, 9.0], "frame"));
  elements.push(cube([1.6, 7.2, 6.6], [2.4, 8.8, 12.4], "strap"));
  elements.push(cube([13.6, 7.2, 6.6], [14.4, 8.8, 12.4], "strap"));
  elements.push(cube([1.6, 7.2, 12.4], [14.4, 8.8, 13.0], "strap"));

  elements.push(cube([3.4, 4.9, 6.0], [6.0, 5.6, 8.6], "dark"));
  elements.push(cube([10.0, 4.9, 6.0], [12.6, 5.6, 8.6], "dark"));
  elements.push(cube([6.2, 5.0, 6.4], [9.8, 5.6, 8.2], "accent"));

  return model({
    particle: "minecraft:block/black_concrete",
    shell: "minecraft:block/black_concrete",
    frame: "minecraft:block/polished_blackstone",
    dark: "minecraft:block/deepslate_tiles",
    lens: "minecraft:block/blue_stained_glass",
    metal: "minecraft:block/iron_block",
    foam: "minecraft:block/gray_wool",
    strap: "minecraft:block/black_wool",
    accent: "minecraft:block/red_concrete"
  }, elements, gogglesDisplay);
}

write("rc_transmitter", transmitterModel(false));
write("rc_transmitter_lr", transmitterModel(true));
write("fpv_goggles", gogglesModel());

function round(value) {
  return Math.round(value * 1000) / 1000;
}

// `quarter` tips the whole part a quarter turn about X and bakes it into the box corners,
// because a 90 deg element rotation is not a legal model rotation. Face slots and any
// element rotation axis move with it.
function transformedPart(name, offset, scale, prefix, groupRotation, quarter = 0) {
  const source = generated.get(name);
  if (!source) throw new Error(`Unknown generated part: ${name}`);
  const textures = Object.fromEntries(Object.entries(source.textures).map(([key, value]) => [`${prefix}_${key}`, value]));
  const spin = values => {
    const d = [values[0] - 8, values[1] - 8, values[2] - 8];
    if (!quarter) return d;
    return quarter > 0 ? [d[0], -d[2], d[1]] : [d[0], d[2], -d[1]];
  };
  const point = values => spin(values).map((value, axis) => round(8 + value * scale + offset[axis]));
  const box = (from, to) => {
    const a = point(from);
    const b = point(to);
    return {
      from: [Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.min(a[2], b[2])],
      to: [Math.max(a[0], b[0]), Math.max(a[1], b[1]), Math.max(a[2], b[2])]
    };
  };
  const faceMap = quarter > 0
    ? { up: "south", south: "down", down: "north", north: "up", east: "east", west: "west" }
    : { up: "north", north: "down", down: "south", south: "up", east: "east", west: "west" };
  const axisMap = quarter > 0
    ? { x: ["x", 1], y: ["z", 1], z: ["y", -1] }
    : { x: ["x", 1], y: ["z", -1], z: ["y", 1] };
  const remap = rotation => {
    const copy = { ...rotation, origin: point(rotation.origin ?? [8, 8, 8]) };
    if (quarter) {
      const [axis, sign] = axisMap[rotation.axis];
      copy.axis = axis;
      copy.angle = round(rotation.angle * sign);
    }
    return copy;
  };
  const elements = source.elements.map(element => {
    const copy = box(element.from, element.to);
    if (element.rotation) copy.rotation = remap(element.rotation);
    else if (groupRotation) copy.rotation = remap(groupRotation);
    copy.faces = Object.fromEntries(Object.entries(element.faces).map(([face, data]) => [
      quarter ? faceMap[face] : face,
      { ...data, texture: data.texture.startsWith("#") ? `#${prefix}_${data.texture.slice(1)}` : data.texture }
    ]));
    return copy;
  });
  return { textures, elements };
}

// Underside of a part once scaled, so electronics can be seated on a deck instead of
// guessed into the frame's interior where nothing is visible.
function partFloor(name, scale) {
  const source = generated.get(name);
  if (!source) throw new Error(`Unknown generated part: ${name}`);
  return 8 + (Math.min(...source.elements.map(element => element.from[1])) - 8) * scale;
}

function seat(name, scale, deckY, dx, dz) {
  return [dx, round(deckY - partFloor(name, scale)), dz];
}

function assembledModel(parts, extraTextures = {}, extraElements = []) {
  const textures = { ...extraTextures };
  const elements = [...extraElements];
  parts.forEach((part, index) => {
    const transformed = transformedPart(part.name, part.offset ?? [0, 0, 0], part.scale ?? 1, `p${index}`,
      part.rotation, part.quarter ?? 0);
    Object.assign(textures, transformed.textures);
    elements.push(...transformed.elements);
  });
  return model(textures, elements, {
    gui: { rotation: [32, 225, 0], translation: [0, -0.4, 0], scale: [0.68, 0.68, 0.68] },
    ground: { translation: [0, 2.5, 0], scale: [0.3, 0.3, 0.3] },
    thirdperson_righthand: { rotation: [70, 45, 0], translation: [0, 2, 0], scale: [0.35, 0.35, 0.35] }
  });
}

const buildSpecs = {
  frame_5inch: { battery: "battery_4s_1300", batteryScale: 0.68, batteryY: 1.6, camera: "camera_standard",
    cameraScale: 0.72, motor: "motor_2207_2450kv", motorScale: 0.72, prop: "prop_5x4x3", propScale: 1.12, propLift: 2.0 },
  frame_7inch: { battery: "battery_6s_1100", batteryScale: 0.72, batteryY: 1.75, camera: "camera_standard",
    cameraScale: 0.78, motor: "motor_2806_1300kv", motorScale: 0.86, prop: "prop_5x4x2", propScale: 1.5, propLift: 2.2 },
  frame_hexa_6inch: { battery: "battery_6s_1100", batteryScale: 0.66, batteryY: 1.7, camera: "camera_standard",
    cameraScale: 0.72, motor: "motor_2306_1700kv", motorScale: 0.7, prop: "prop_5x4x3", propScale: 0.68, propLift: 1.9 },
  frame_octo_8inch: { battery: "battery_6s_1100", batteryScale: 0.72, batteryY: 1.75, camera: "camera_wide",
    cameraScale: 0.72, motor: "motor_3115_900kv", motorScale: 0.8, prop: "prop_5x4x2", propScale: 0.68, propLift: 2.1 },
  frame_interceptor: { battery: "battery_4s_1300", batteryScale: 0.46, batteryOffset: [0, 0.6, 0.2],
    camera: "camera_standard", cameraScale: 0.52, motor: "motor_2207_2450kv", motorScale: 0.6,
    prop: "prop_5x4x3", propScale: 0.52, propLift: 1.85, axial: true, moduleScale: 0.62,
    moduleOffset: [0, 0, -3.6], deckY: 10.25, fcScale: 0.36, fcAt: [0, 0.2], vtxScale: 0.32, vtxAt: [0, 2.8] }
};

function droneParts(frameName, moduleName = "module_payload_bay") {
  const spec = frameSpecs[frameName];
  const build = buildSpecs[frameName];
  // Deck level: the stack and the video transmitter ride on top of the frame plate,
  // fore and aft of the battery, so both read on the assembled model.
  const deck = build.deckY ?? 9.74;
  const fcScale = build.fcScale ?? 0.48;
  const vtxScale = build.vtxScale ?? 0.42;
  const fcAt = build.fcAt ?? [0, -3.1];
  const vtxAt = build.vtxAt ?? [-1.5, 3.4];
  const parts = [
    { name: frameName },
    { name: "fc_h7", offset: seat("fc_h7", fcScale, deck, fcAt[0], fcAt[1]), scale: fcScale },
    { name: "vtx_800mw", offset: seat("vtx_800mw", vtxScale, deck, vtxAt[0], vtxAt[1]), scale: vtxScale },
    { name: build.battery, offset: build.batteryOffset ?? [0, build.batteryY, 0.8], scale: build.batteryScale },
    {
      name: build.camera,
      offset: build.cameraOffset ?? [0, 0.65, 0.6 - spec.cameraZ],
      scale: build.cameraScale,
      rotation: build.axial ? undefined : rot("x", 22.5)
    },
    { name: moduleName, offset: build.moduleOffset ?? [0, -0.75, 0], scale: build.moduleScale ?? 0.78 }
  ];
  for (const [x, y, z] of spec.mounts) {
    const offset = [x - 8, y - 8, z - 8];
    if (build.axial) {
      parts.push({ name: build.motor, offset, scale: build.motorScale, quarter: 1 });
      parts.push({
        name: build.prop,
        offset: [offset[0], offset[1], offset[2] + build.propLift],
        scale: build.propScale,
        quarter: 1
      });
      continue;
    }
    parts.push({ name: build.motor, offset, scale: build.motorScale });
    parts.push({ name: build.prop, offset: [offset[0], offset[1] + build.propLift, offset[2]], scale: build.propScale });
  }
  return parts;
}

function quadParts(moduleName = "module_payload_bay") {
  return droneParts("frame_5inch", moduleName);
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
  const deck = cine ? 9.62 : 9.4;
  const fcScale = cine ? 0.5 : 0.38;
  const vtxName = cine ? "vtx_200mw" : "vtx_25mw";
  const vtxScale = cine ? 0.46 : 0.34;
  const batteryName = cine ? "battery_4s_650" : "battery_1s_300";
  const batteryScale = cine ? 0.62 : 0.5;
  const parts = [
    { name: cine ? "frame_cinewhoop" : "frame_tiny_whoop" },
    { name: "fc_f411", offset: seat("fc_f411", fcScale, deck, 0, cine ? -2.4 : -1.9), scale: fcScale },
    { name: vtxName, offset: seat(vtxName, vtxScale, deck, cine ? -2.1 : -1.7, cine ? 0.4 : 0.5), scale: vtxScale },
    {
      name: batteryName,
      offset: seat(batteryName, batteryScale, deck + (cine ? 0.95 : 0.8), 0, cine ? 1.7 : 1.3),
      scale: batteryScale
    },
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
      scale: cine ? 0.82 : 0.84
    });
  }
  return parts;
}

write("assembled_drone_whoop", assembledModel(whoopParts(false)));
write("assembled_drone_cinewhoop", assembledModel(whoopParts(true)));
write("assembled_drone_longrange", assembledModel(droneParts("frame_7inch")));
write("assembled_drone_hexa", assembledModel(droneParts("frame_hexa_6inch", "module_warhead")));
write("assembled_drone_octo", assembledModel(droneParts("frame_octo_8inch", "module_storage")));
write("assembled_drone_interceptor", assembledModel(droneParts("frame_interceptor", "module_warhead")));

// Stock and NBT-driven drone items now have a readable 3D preview instead of a flat generated sprite.
// The stock drone item carries its build in NBT, so the frame property picks the matching preview.
write("modular_drone", {
  parent: "fpv:item/assembled_drone_quad",
  overrides: [
    { predicate: { "fpv:frame": 1 }, model: "fpv:item/assembled_drone_whoop" },
    { predicate: { "fpv:frame": 2 }, model: "fpv:item/assembled_drone_cinewhoop" },
    { predicate: { "fpv:frame": 3 }, model: "fpv:item/assembled_drone_longrange" },
    { predicate: { "fpv:frame": 4 }, model: "fpv:item/assembled_drone_hexa" },
    { predicate: { "fpv:frame": 5 }, model: "fpv:item/assembled_drone_octo" },
    { predicate: { "fpv:frame": 6 }, model: "fpv:item/assembled_drone_interceptor" }
  ]
});
write("modular_plane", {
  parent: "fpv:item/plane",
  overrides: [
    { predicate: { "fpv:frame": 1 }, model: "fpv:item/plane_delta" },
    { predicate: { "fpv:frame": 2 }, model: "fpv:item/plane_glider" },
    { predicate: { "fpv:frame": 3 }, model: "fpv:item/plane_longrange" },
    { predicate: { "fpv:frame": 4 }, model: "fpv:item/plane_cargo" },
    { predicate: { "fpv:frame": 5 }, model: "fpv:item/plane_interceptor" },
    { predicate: { "fpv:frame": 6 }, model: "fpv:item/plane_scout" },
    { predicate: { "fpv:frame": 7 }, model: "fpv:item/plane_kamikaze" },
    { predicate: { "fpv:frame": 8 }, model: "fpv:item/plane_carrier" },
    { predicate: { "fpv:frame": 9 }, model: "fpv:item/plane_shahed" }
  ]
});
write("racing_drone", { parent: "fpv:item/assembled_drone_quad" });
write("remote_racing_drone", { parent: "fpv:item/assembled_drone_remote" });
write("kamikaze_drone", { parent: "fpv:item/assembled_drone_kamikaze" });
write("tiny_whoop", { parent: "fpv:item/assembled_drone_whoop" });
write("cinewhoop", { parent: "fpv:item/assembled_drone_cinewhoop" });

console.log(`Generated FPV item models in ${output}`);
