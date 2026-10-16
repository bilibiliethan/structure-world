import gzip, struct, sys, json

def read_nbt(path):
    with gzip.open(path, 'rb') as f:
        data = f.read()
    pos = [0]
    def u1():
        v = data[pos[0]]; pos[0] += 1; return v
    def u2():
        v = struct.unpack('>H', data[pos[0]:pos[0]+2])[0]; pos[0] += 2; return v
    def i4():
        v = struct.unpack('>i', data[pos[0]:pos[0]+4])[0]; pos[0] += 4; return v
    def i8():
        v = struct.unpack('>q', data[pos[0]:pos[0]+8])[0]; pos[0] += 8; return v
    def f4():
        v = struct.unpack('>f', data[pos[0]:pos[0]+4])[0]; pos[0] += 4; return v
    def f8():
        v = struct.unpack('>d', data[pos[0]:pos[0]+8])[0]; pos[0] += 8; return v
    def name():
        n = u2(); s = data[pos[0]:pos[0]+n].decode('utf-8', 'replace'); pos[0] += n; return s
    def payload(t):
        if t == 1:  # byte
            v = data[pos[0]]; pos[0] += 1
            return v - 256 if v > 127 else v
        if t == 2:  # short
            v = u2()
            return v - 65536 if v > 32767 else v
        if t == 3: return i4()
        if t == 4: return i8()
        if t == 5: return f4()
        if t == 6: return f8()
        if t == 7:
            n = i4(); s = data[pos[0]:pos[0]+n]; pos[0] += n; return list(s)
        if t == 8: return name()
        if t == 9:
            et = u1(); n = i4(); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = u1()
                if tt == 0: return d
                nm = name()
                d[nm] = payload(tt)
        if t == 11:
            n = i4(); return [i4() for _ in range(n)]
        if t == 12:
            n = i4(); return [i8() for _ in range(n)]
        raise ValueError(f'unknown tag {t}')
    t = u1()
    if t != 10:
        raise ValueError('root not compound')
    _ = name()
    return payload(10)

for path in sys.argv[1:]:
    print('=' * 20, path)
    try:
        nbt = read_nbt(path)
    except Exception as e:
        print('  解析失败:', e)
        continue
    d = nbt.get('Data', nbt)
    for k in ('SpawnX', 'SpawnY', 'SpawnZ', 'SpawnAngle', 'LevelName', 'GameType', 'Version', 'WorldGenSettings'):
        if k in d:
            v = d[k]
            if k == 'WorldGenSettings':
                v = json.dumps(v, ensure_ascii=False)[:1500]
            print(f'  {k} = {v}')
    print('  ---- 全部顶层键 ----')
    print('  ', sorted(d.keys()))
