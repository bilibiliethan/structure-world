# -*- coding: utf-8 -*-
"""扫描 region 文件，输出地形/生物群系概览图。
用法: python scan_terrain.py <region.mca> [<region.mca> ...]
每个字符 = 一个区块: '#' 陆地为主, '~' 水为主, '+' 各半, '?' 未生成
"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from read_chunk import load_chunk, heightmap_values
import collections

SEA = 63


def analyse(path, cx, cz):
    nbt, err = load_chunk(path, cx, cz)
    if nbt is None:
        return None
    st = nbt.get('Status')
    if st != 'minecraft:full':
        return {'status': st}
    hm = nbt.get('Heightmaps') or {}
    ws = heightmap_values(hm['WORLD_SURFACE'], -64)
    of = heightmap_values(hm['OCEAN_FLOOR'], -64)
    dry = sum(1 for i in range(256) if ws[i] > SEA and ws[i] >= of[i])
    water = sum(1 for i in range(256) if ws[i] <= SEA)
    biomes = set()
    for s in nbt.get('sections') or []:
        b = s.get('biomes') or {}
        for e in (b.get('palette') or []):
            biomes.add(e if isinstance(e, str) else e.get('Name'))
    return {'status': st, 'dry': dry, 'water': water,
            'ws_min': min(ws), 'ws_max': max(ws), 'of_min': min(of), 'of_max': max(of),
            'biomes': biomes, 'hm': hm}


def char_for(r):
    if r is None:
        return ' '
    if r.get('status') != 'minecraft:full':
        return '?'
    d, w = r['dry'], r['water']
    if d >= 240:
        return '#'
    if d >= 40:
        return '+'
    if d > 0:
        return '.'
    return '~'


def main():
    if len(sys.argv) < 2:
        # 默认扫世界 (4)
        base = r'D:/pcl/.minecraft/versions/1.21.11-Fabric 0.19.3/saves/新的世界 (4)/region'
        if not os.path.isdir(base):
            base = os.path.dirname(base)
        files = [os.path.join(base, f) for f in sorted(os.listdir(base)) if f.endswith('.mca')]
    else:
        files = sys.argv[1:]

    grid = {}
    allbiomes = collections.Counter()
    for path in files:
        print('=== 扫描', path)
        try:
            with open(path, 'rb') as f:
                head = f.read(4096)
            if len(head) < 4096:
                continue
        except Exception as e:
            print('  打开失败', e); continue
        for lz in range(32):
            for lx in range(32):
                loc = int.from_bytes(head[4 * (lx + lz * 32):4 * (lx + lz * 32) + 4], 'big')
                if loc >> 8 == 0:
                    continue
                # 从文件名推 region 原点
                name = os.path.basename(path).replace('.mca', '')
                parts = name.split('.')
                try:
                    rx, rz = int(parts[1]), int(parts[2])
                except Exception:
                    rx, rz = 0, 0
                cx, cz = rx * 32 + lx, rz * 32 + lz
                try:
                    r = analyse(path, cx, cz)
                except Exception as e:
                    print('  区块 (%d,%d) 解析异常 %s' % (cx, cz, e)); continue
                if r is None:
                    continue
                grid[(cx, cz)] = r
                if r.get('biomes'):
                    allbiomes.update(r['biomes'])
                if r.get('status') == 'minecraft:full':
                    print('  区块 (%3d,%3d) 干地=%3d/256 水=%3d  WSurface[%d..%d] OFloor[%d..%d] 生物群系=%s'
                          % (cx, cz, r['dry'], r['water'], r['ws_min'], r['ws_max'],
                             r['of_min'], r['of_max'], ','.join(sorted(b for b in r['biomes'] if b))))
    print()
    print('=== 生物群系统计(全区域) ===')
    for b, n in allbiomes.most_common():
        print('   %-32s %d' % (b, n))
    print()
    print('=== 地形图 (每字符=1区块, 行=z 从上到下 -32..31, 列=x -32..31) ===')
    print('    ' + ''.join(str(abs(x) // 10 % 10) for x in range(-32, 32)))
    print('    ' + ''.join(str(abs(x) % 10) for x in range(-32, 32)))
    for cz in range(-32, 32):
        row = ''.join(char_for(grid.get((cx, cz))) for cx in range(-32, 32))
        print('%4d %s' % (cz, row))


if __name__ == '__main__':
    main()
