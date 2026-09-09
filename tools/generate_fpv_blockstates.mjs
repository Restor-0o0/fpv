import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const out = join(dirname(fileURLToPath(import.meta.url)), "..",
  "src", "main", "resources", "assets", "fpv", "blockstates");

const YAW = { north: 0, east: 90, south: 180, west: 270 };
const write = (n, v) => writeFileSync(join(out, `${n}.json`), JSON.stringify(v, null, 2) + "\n");

function horizontal(name) {
  const variants = {};
  for (const [dir, y] of Object.entries(YAW)) {
    variants[`facing=${dir}`] = y ? { model: `fpv:block/${name}`, y } : { model: `fpv:block/${name}` };
  }
  write(name, { variants });
}

["control_station", "kamikaze_station", "drone_nest", "assembly_station"].forEach(horizontal);

const charging = {};
for (const [dir, y] of Object.entries(YAW)) {
  for (const on of [false, true]) {
    const v = { model: `fpv:block/charging_station${on ? "_active" : ""}` };
    if (y) v.y = y;
    charging[`charging=${on},facing=${dir}`] = v;
  }
}
write("charging_station", { variants: charging });

const WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
  "mangrove", "cherry", "bamboo", "crimson", "warped"];
const wb = {};
for (const w of WOODS) {
  for (const [dir, y] of Object.entries(YAW)) {
    const v = { model: `fpv:block/workbench_${w}` };
    if (y) v.y = y;
    wb[`facing=${dir},wood=${w}`] = v;
  }
}
write("workbench", { variants: wb });
