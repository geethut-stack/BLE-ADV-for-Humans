import sys, types, importlib, json, os
"""Generates app/src/test/.../GoldenVectors.kt from the REAL Python codecs of ha-ble-adv.

Usage:  HA_BLE_ADV=./ha-ble-adv python3 tools/gen_vectors2.py          (all codecs)
"""
HA = os.environ.get("HA_BLE_ADV", "./ha-ble-adv")
pkg = types.ModuleType("ble_adv"); pkg.__path__ = [os.path.join(HA, "custom_components", "ble_adv")]
sys.modules["ble_adv"] = pkg
cm = importlib.import_module("ble_adv.codecs")
from ble_adv.codecs.models import BleAdvEncCmd, BleAdvConfig, BleAdvAdvertisement, BleAdvEntAttr

PORTED = set(sys.argv[1].split(",")) if len(sys.argv) > 1 else None
codecs = [c for c in cm.get_codec_list() if PORTED is None or type(c).__name__ in PORTED]
print(len(codecs), "ported codecs")

def lit(v):
    if v is None: return "null"
    if isinstance(v, bool): return "true" if v else "false"
    if isinstance(v, int): return "%dL" % v
    if isinstance(v, float): return repr(v)
    if isinstance(v, str): return json.dumps(v)
    if isinstance(v, (list, tuple)): return "listOf(" + ", ".join(lit(x) for x in v) + ")"
    raise TypeError(v)

def mk_conf(c):
    cls = type(c).__name__
    params = list(c.match_params)
    if cls == "SmartElfinEncoder": params = [[0x11, 0x22, 0x33, 0x44]]
    if cls.startswith("FanLampEncoderV2"): cid = 0xD2135C22
    elif cls in ("FanLampEncoderV1R0", "FanLampEncoderV1R1"): cid = 0x5022
    elif cls.startswith("FanLampEncoderV1"): cid = 0x3D5022
    elif cls in ('MantraEncoder','HxlightCodec','LedSpotCodec','ZhijiaEncoderV0','ZhimeiEncoderV0','ZhimeiEncoderV2'): cid = 0xB2C3
    else: cid = 0x00A1B2C3
    conf = BleAdvConfig(cid, 2, params)
    conf.tx_count = 7
    conf.seed = 0x1234 if c._seed_max > 0xFF else 0x5A
    if c._seed_max == 0: conf.seed = 0
    if cls.startswith("FanLamp"): conf.seed = 0x1234 if c._seed_max else 0
    return conf, params

def e_(cmd, **kw):
    e = BleAdvEncCmd(cmd)
    for k, v in kw.items(): setattr(e, k, v)
    return e
cmds = [e_(0x10), e_(0x11), e_(0x21, param=1, arg0=127, arg1=40, arg2=9), e_(0x28),
        e_(0x33, param=3, arg0=5, arg1=6, arg2=7, arg3=8), e_(0x44, param=2, arg0=200, arg1=100)]

enc_lines = []
nodec = []
for c in codecs:
    for e in cmds:
        conf, params = mk_conf(c)
        seed0, tx0 = conf.seed, conf.tx_count
        a = c.encode_advs(e, conf)[0]
        de, dc = c.decode_adv(BleAdvAdvertisement(a.ble_type, a.raw, a.ad_flag, a.second_type, a.second_raw))
        if de is None: nodec.append((c.codec_id, e.cmd)); dec = "null"
        else:
            cp = dc.codec_params
            dsign = "true" if (cp and cp[0] is True) else "false"
            dpre = lit(list(cp[1])) if (cp and len(cp) > 1 and isinstance(cp[1], (list, tuple))) else "emptyList<Long>()"
            dextra = lit(list(cp[0])) if (cp and isinstance(cp[0], (list, tuple))) else "emptyList<Long>()"
            dec = f"D({de.cmd}, {de.param}, {de.arg0}, {de.arg1}, {de.arg2}, {de.arg3}, {de.arg4}, {dc.id}L, {dc.index}, {dc.tx_count}, {dc.seed}L, {dsign}, {dpre}, {dextra})"
        enc_lines.append(f'V("{c.codec_id}", {e.cmd}, {e.param}, {e.arg0}, {e.arg1}, {e.arg2}, {e.arg3}, {e.arg4}, {conf.id}L, {conf.index}, {seed0}L, {tx0}, {lit(params)}, {a.ble_type}, {a.ad_flag if a.ad_flag is not None else "null"}, "{a.raw.hex()}", {a.second_type}, {("null" if a.second_raw is None else chr(34)+a.second_raw.hex()+chr(34))}, {conf.tx_count}, {conf.seed}L, {dec})')
print("encode vectors:", len(enc_lines), "no-decode:", nodec[:8], len(nodec))

# ---- translator vectors
seen = {}
tv_lines = []
for c in codecs:
    for sname, tset in c.get_translator_sets().items():
        for t in tset:
            if not t.direct: continue
            ent = t.ent.create()
            attrs = dict(ent.attrs)
            for (ae, _, _) in t._copies: attrs.setdefault(ae, 0.37)
            if t._scopy: attrs.setdefault(t._scopy[0], 600.0)
            if not type(t).__module__.endswith('.models'):
                for k_, v_ in (('r', 0.37), ('g', 0.5), ('b', 0.9), ('on', True), ('speed', 3), ('dir', True), ('osc', False), ('preset', None)): attrs.setdefault(k_, v_)
            chg = list(t.ent._actions)
            for t2 in tset:
                for k2, v2 in list(t2.ent.mins.items()) + list(t2.ent.maxs.items()): attrs.setdefault(k2, v2)
            e = BleAdvEntAttr(chg, attrs, t.ent._base_type, t.ent._index)
            outs = c.ent_to_enc(e, sname)
            key = json.dumps([t.ent._base_type, t.ent._index, sorted(chg), attrs, [(o.cmd, o.param, o.arg0, o.arg1, o.arg2, o.arg3, o.arg4) for o in outs]], sort_keys=True, default=str)
            if key in seen: continue
            seen[key] = 1
            exp = ", ".join(f"intArrayOf({o.cmd}, {o.param}, {o.arg0}, {o.arg1}, {o.arg2}, {o.arg3}, {o.arg4})" for o in outs)
            amap = ", ".join(f'{json.dumps(k)} to {lit(v)}' for k, v in attrs.items())
            tv_lines.append(f'TV("{c.codec_id}", {json.dumps(sname)}, "{t.ent._base_type}", {t.ent._index}, listOf({", ".join(json.dumps(x) for x in chg)}), mapOf<String, Any?>({amap}), listOf({exp}))')
print("translator vectors:", len(tv_lines))

def chunked(name, typ, lines, n=60):
    body = []
    for i in range(0, len(lines), n):
        body.append(f"    private fun {name}{i//n}(): List<{typ}> = listOf(\n" + "\n".join("        " + l + "," for l in lines[i:i+n]) + "\n    )")
    calls = " + ".join(f"{name}{j}()" for j in range((len(lines)+n-1)//n)) or "emptyList()"
    return "\n\n".join(body) + f"\n\n    val {name}: List<{typ}> by lazy {{ {calls} }}\n"

cw = []
def cww(br255, k, rev):
    br = br255 / 255.0; ct = 1.0 - ((k - 2000) / float(6535 - 2000))
    if rev: ct = 1.0 - ct
    cold = min(1.0, (1.0 - ct) * 2.0); warm = min(1.0, ct * 2.0)
    return int(255 * (br * cold)), int(255 * (br * warm))
for br in (255, 127, 64, 8):
    for k in (2000, 2800, 4000, 5200, 6535):
        for rev in (False, True):
            cc, ww = cww(br, k, rev); cw.append(f"CW({br}, {k}, {str(rev).lower()}, {cc}, {ww})")

src = '''package com.lampremote.core

/** GENERATED from the real ble_adv Python codecs (gen_vectors2.py). Do not edit. */
class D(val cmd: Int, val param: Int, val a0: Int, val a1: Int, val a2: Int, val a3: Int, val a4: Int, val id: Long, val index: Int,
        val tx: Int, val seed: Long, val withSign: Boolean, val prefix: List<Long>, val extra: List<Long>)

class V(val codec: String, val cmd: Int, val param: Int, val a0: Int, val a1: Int, val a2: Int, val a3: Int, val a4: Int,
        val id: Long, val index: Int, val seed0: Long, val tx0: Int, val params: List<Any?>,
        val type: Int, val flag: Int?, val raw: String, val secondType: Int, val secondRaw: String?,
        val txAfter: Int, val seedAfter: Long, val dec: D?)

class TV(val codec: String, val set: String, val base: String, val index: Int, val chg: List<String>,
         val attrs: Map<String, Any?>, val expected: List<IntArray>)

class CW(val br: Int, val kelvin: Int, val reversed: Boolean, val cold: Int, val warm: Int)

object GoldenVectors {
''' + chunked("encode", "V", enc_lines) + "\n" + chunked("translators", "TV", tv_lines) + '''
    val coldWarm: List<CW> = listOf(
''' + ",\n".join("        " + x for x in cw) + ''',
    )
}
'''
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "test", "java", "com", "lampremote", "core", "GoldenVectors.kt")
open(out, "w").write(src)
print("written", os.path.getsize(out), "bytes")
