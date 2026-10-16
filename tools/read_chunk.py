# -*- coding: utf-8 -*-
"""从 region 文件里直接读取某个区块的地面真值：Status / Heightmaps / 生物群系 / 顶层方块。
用法: python read_chunk.py <region.mca> <chunkX> <chunkZ>
"""
import struct, sys, zlib, gzip, io

# ---------- 通用 NBT 解析 ----------
class NBT:
    def __init__(self, data):
        self.d = data
        self.p = 0

    def u1(self):
        v = self.d[self.p]; self.p += 1; return v

    def u2(self):
        v = struct.unpack_from('>H', self.d, self.p)[0]; self.p += 2; return v

    def i4(self):
        v = struct.unpack_from('>i', self.d, self.p)[0]; self.p += 4; return v

    def i8(self):
        v = struct.unpack_from('>q', self.d, self.p)[0]; self.p += 8; return v

    def f4(self):
        v = struct.unpack_from('>f', self.d, self.p)[0]; self.p += 4; return v

    def f8(self):
        v = struct.unpack_from('>d', self.d, self.p)[0]; self.p += 8; return v

    def name(self):
        n = self.u2()
        s = self.d[self.p:self.p + n].decode('utf-8', 'replace'); self.p += n
        return s

    def payload(self, t):
        if t == 1:
            v = self.u1(); return v - 256 if v > 127 else v
        if t == 2:
            v = self.u2(); return v - 65536 if v > 32767 else v
        if t == 3: return self.i4()
        if t == 4: return self.i8()
        if t == 5: return self.f4()
        if t == 6: return self.f8()
        if t == 7:
            n = self.i4(); s = self.d[self.p:self.p + n]; self.p += n; return s
        if t == 8: return self.name()
        if t == 9:
            et = self.u1(); n = self.i4(); return [self.payload(et) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = self.u1()
                if tt == 0: return out
                nm = self.name()
                out[nm] = self.payload(tt)
        if t == 11:
            n = self.i4(); return list(struct.unpack_from('>%di' % n, self.d, self.p)) if not self._skip_i4(n) else None
        if t == 12:
            n = self.i4(); v = list(struct.unpack_from('>%dq' % n, self.d, self.p)); self.p += 8 * n; return v
        raise ValueError('tag %d' % t)

    def _skip_i4(self, n):
        self.p += 4 * n
        return True

    def root(self):
        t = self.u1()
        if t != 10:
            raise ValueError('root not compound, tag=%d' % t)
        self.name()
        return self.payload(10)


def load_chunk(region_path, cx, cz):
    lx, lz = cx & 31, cz & 31
    with open(region_path, 'rb') as f:
        raw = f.read()
    off = 4 * (lx + lz * 32)
    loc = struct.unpack_from('>I', raw, off)[0]
    sector = loc >> 8
    if sector == 0:
        return None, 'location 表为空(该区块未写入)'
    start = sector * 4096
    length = struct.unpack_from('>I', raw, start)[0]
    ctype = raw[start + 4]
    blob = raw[start + 5:start + 4 + length]
    if ctype == 1:
        data = gzip.decompress(blob)
    elif ctype == 2:
        data = zlib.decompress(blob)
    elif ctype == 3:
        data = blob
    else:
        return None, '未知压缩类型 %d' % ctype
    return NBT(data).root(), None


def unpack_bits(longs, bits, count):
    """Minecraft 非跨 long 边界的位打包解包"""
    mask = (1 << bits) - 1
    per = 64 // bits
    out = []
    for i in range(0, len(longs)):
        v = longs[i] & 0xFFFFFFFFFFFFFFFF
        base = i * per
        for j in range(per):
            if base + j >= count:
                return out
            out.append((v >> (j * bits)) & mask)
    return out


def heightmap_values(longs, bottom_y):
    vals = unpack_bits(longs, 9, 256)
    return [v + bottom_y for v in vals]


def main():
    region, cx, cz = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
    nbt, err = load_chunk(region, cx, cz)
    if nbt is None:
        print('  区块 (%d,%d) 读取失败: %s' % (cx, cz, err)); return
    print('  区块 (%d,%d)  顶层键: %s' % (cx, cz, sorted(nbt.keys())))
    print('  Status      =', nbt.get('Status'))
    print('  xPos/zPos   =', nbt.get('xPos'), nbt.get('zPos'))
    print('  DataVersion =', nbt.get('DataVersion'))
    hm = nbt.get('Heightmaps') or {}
    for k in ('WORLD_SURFACE', 'OCEAN_FLOOR', 'MOTION_BLOCKING', 'MOTION_BLOCKING_NO_LEAVES'):
        if k in hm:
            vals = heightmap_values(hm[k], -64)
            print('  %-26s min=%d max=%d  第0行(z=0): %s' % (k, min(vals), max(vals), vals[:16]))
    sections = nbt.get('sections') or []
    print('  段数 =', len(sections))
    for s in sections:
        y = s.get('Y')
        bs = (s.get('block_states') or {}).get('palette') or []
        names = [b.get('Name') for b in bs]
        biomes = s.get('biomes') or {}
        bpal = biomes.get('palette') or []
        bnames = [b if isinstance(b, str) else b.get('Name') for b in bpal]
        if y is not None and 3 <= y <= 6:
            print('   段 Y=%2d 方块(%d): %s' % (y, len(names), names[:14]))
            print('           生物群系: %s' % (bnames[:8],))
    for key in ('block_entities', 'entities', 'structures'):
        if key in nbt:
            v = nbt[key]
            print('  %s: %d 项' % (key, len(v)))


if __name__ == '__main__':
    main()
