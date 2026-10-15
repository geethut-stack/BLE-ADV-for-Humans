import sys, types, importlib, json, os
from urllib.parse import quote
"""Generates app/src/main/resources/codec_specs.txt from the Python codecs of ha-ble-adv.

Usage:  git clone https://github.com/NicoIIT/ha-ble-adv ; pip install pycryptodome   (Python 3.12+)
        HA_BLE_ADV=./ha-ble-adv python3 tools/gen_specs.py
"""
HA = os.environ.get("HA_BLE_ADV", "./ha-ble-adv")
pkg = types.ModuleType("ble_adv"); pkg.__path__ = [os.path.join(HA, "custom_components", "ble_adv")]
sys.modules["ble_adv"] = pkg
cm = importlib.import_module("ble_adv.codecs")
const = importlib.import_module("ble_adv.codecs.const")
lst = cm.get_codec_list()

def enc(v):
    if v is None: return "n:"
    if isinstance(v, bool): return "b:%d" % int(v)
    if isinstance(v, int): return "i:%d" % v
    if isinstance(v, float): return "f:%r" % v
    if isinstance(v, str): return "s:" + quote(v, safe="")
    if isinstance(v, (bytes, bytearray)): return "x:" + bytes(v).hex()
    if isinstance(v, (list, tuple)): return "l:" + ",".join(quote(enc(e), safe="") for e in v)
    raise TypeError(type(v))

def encmap(d): return ";".join(quote(k, safe="") + "=" + quote(enc(v), safe="") for k, v in d.items())

BASE = {'codec_id','match_id','match_params','_header','_prefix','_footer','_ble_type','_ad_flag','_len','_header_start_pos',
        '_tx_step','_tx_max','_seed_max','_translator_maps'}
out = []
trans_ids = {}
trans_lines = []
def trans_id(t):
    key = (t.ent._base_type, t.ent._index, tuple(t.ent._actions), json.dumps(t.ent.eqs, sort_keys=True, default=str),
           json.dumps(t.ent.mins, sort_keys=True), json.dumps(t.ent.maxs, sort_keys=True), t.enc._cmd,
           json.dumps(t.enc.eqs, sort_keys=True), json.dumps(t.enc.mins, sort_keys=True), json.dumps(t.enc.maxs, sort_keys=True),
           json.dumps(t._copies), json.dumps(t._scopy), t.direct, t.reverse, type(t).__name__ + type(t).__module__)
    if key in trans_ids: return trans_ids[key]
    i = len(trans_ids); trans_ids[key] = i
    copies = ";".join(quote(a, safe="") + "," + quote(b, safe="") + "," + repr(float(f)) for a, b, f in t._copies)
    sc = "" if t._scopy is None else "%s,%s,%r,%d" % (quote(t._scopy[0], safe=""), "+".join(quote(d, safe="") for d in t._scopy[1]), float(t._scopy[2]), t._scopy[3])
    trans_lines.append("|".join(["TRANS", str(i), t.ent._base_type, str(t.ent._index), ",".join(quote(a, safe="") for a in t.ent._actions),
        encmap(t.ent.eqs), encmap(t.ent.mins), encmap(t.ent.maxs), str(t.enc._cmd), encmap(t.enc.eqs), encmap(t.enc.mins), encmap(t.enc.maxs),
        copies, sc, "1" if t.direct else "0", "1" if t.reverse else "0",
        "" if type(t).__module__.endswith(".models") else type(t).__module__.split(".")[-1] + "." + type(t).__name__]))
    return i

for c in lst:
    attrs = {k: v for k, v in vars(c).items() if k not in BASE and not k.startswith("__")}
    # second adv fields: find them whatever their private names are
    sec_type = getattr(c, "second_type", None); sec_type = sec_type if sec_type is not None else getattr(c, "_second_type", None)
    sec_raw = getattr(c, "second_raw", None); sec_raw = sec_raw if sec_raw is not None else getattr(c, "_second_raw", None)
    attrs = {k: v for k, v in attrs.items() if k not in ("second_type", "second_raw", "_second_type", "_second_raw")}
    out.append("|".join(["CODEC", c.codec_id, c.match_id, type(c).__name__, str(c._ble_type), str(-1 if c._ad_flag is None else c._ad_flag),
        str(c._len), str(c._header_start_pos), str(c._tx_step), str(c._tx_max), str(c._seed_max),
        str(-1 if sec_type is None else sec_type), c.DEF_TRANS_NAME, bytes(c._header).hex(), bytes(c._prefix).hex(), bytes(c._footer).hex(),
        "" if sec_raw is None else bytes(sec_raw).hex(), quote(enc(list(c.match_params)), safe=""), encmap(attrs)]))
    for name, tset in c.get_translator_sets().items():
        ids = [trans_id(t) for t in tset]
        out.append("|".join(["TSET", c.codec_id, quote(name, safe=""), ",".join(map(str, ids))]))

apps = []
for nm, lst2 in cm.PHONE_APPS.items():
    apps.append("|".join(["APP", quote(nm, safe=""), ";".join(quote(cid, safe="") + "," + quote(enc(list(pr)), safe="") for cid, pr in lst2)]))
out += apps
os.makedirs(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "resources"), exist_ok=True)
path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "resources", "codec_specs.txt")
open(path, "w").write("\n".join(trans_lines + out) + "\n")
print(len(lst), "codecs;", len(trans_ids), "unique translators;", os.path.getsize(path), "bytes")
print("sets:", {c.codec_id: list(c.get_translator_sets().keys()) for c in lst if len(c.get_translator_sets()) > 1})
