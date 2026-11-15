#!/usr/bin/env python3
"""
Builds the app's offline VIN data (app/src/main/assets/vpic/vin-data.txt.gz) from NHTSA's vPIC
standalone database, so a VIN can be decoded on the phone without sending it anywhere.

Source: https://vpic.nhtsa.dot.gov/downloads/ (the "PostgreSQL plain" zip). vPIC is US
government data and in the public domain.

    python3 tools/vpic/build_subset.py vPICList_lite_YYYY_MM.plain.zip app/src/main/assets/vpic/vin-data.txt.gz

What is kept: cars, MPVs (SUVs, vans) and trucks, for VIN schemas used from 1996 on (OBD2 years),
and only the details the app shows. Everything else (trailers, motorcycles, buses, safety
equipment lists, NCSA codes, private fields) is left out to keep the file small.

File format (UTF-8, tab-separated, one record per line), in the order the app reads it:
  # header lines
  w  wmi  make  manufacturer  country  vehicle type  links  L   links = schema:yearFrom:yearTo,...; L = 1 for a car,
                                                                MPV or light truck (position 7 then fixes the year's cycle)
  s  schema                                                     starts a schema's patterns
     keys  value  [days]                                        a pattern (value #element: read from the VIN). The first in a schema gives the day it was last
                                                                changed (days since 2000-01-01); each after it, how many
                                                                days older it is than the one before (none: same day)
  e  engine code  element  text  [element  text ...]           details vPIC knows for an engine code, used where the
                                                                VIN patterns don't say
  x  vehicle type  models  years                               starts a vehicle spec schema: details vPIC adds for a model
                                                                (models = model values; years blank: every year)
     keys  details  days                                        keys and details as element=value;..., used when every key
                                                                matches what the VIN gave; days as for patterns, absolute
  v  value  element  text  [make]                               values, numbered from 0 in order
Patterns in a schema are listed newest first, as vPIC ranks them. Record letters are lower case so
they can't be mistaken for a pattern's keys, which are upper case.
"""
import csv, datetime, gzip, io, re, sys, zipfile

csv.field_size_limit(10 ** 9)

# vPIC element id -> the app's name for it. Values of elements with a lookup table are stored as text.
ELEMENTS = {
    28: 'model', 34: 'series', 38: 'trim', 5: 'body', 14: 'doors', 15: 'drive',
    9: 'cylinders', 13: 'litres', 64: 'layout', 18: 'engine', 24: 'fuel', 126: 'electrification', 135: 'turbo',
    71: 'hp', 37: 'transmission', 31: 'plant_city', 77: 'plant_state', 75: 'plant_country',
}
VEHICLE_TYPES = {2, 3, 7}   # passenger car, truck, MPV
FIRST_YEAR = 1996           # OBD2
EPOCH = datetime.datetime(2000, 1, 1)


def tables(zip_path, wanted):
    """Reads the COPY blocks of the wanted tables out of the plain SQL dump: {table: [row dict]}."""
    out = {t: [] for t in wanted}
    with zipfile.ZipFile(zip_path) as z:
        name = next(n for n in z.namelist() if n.endswith('.sql'))
        with z.open(name) as raw:
            cols = table = None
            for line in io.TextIOWrapper(raw, encoding='utf-8', errors='replace'):
                if table is None:
                    m = re.match(r'^COPY vpic\.(\w+) \((.*)\) FROM stdin;', line)
                    if m and m.group(1) in wanted:
                        table, cols = m.group(1), [c.strip() for c in m.group(2).split(',')]
                elif line.startswith('\\.'):
                    table = None
                else:
                    out[table].append(dict(zip(cols, line.rstrip('\n').split('\t'))))
    return out


def null(v):
    return None if v in (None, '\\N', '') else v


def days(row):
    when = null(row.get('updatedon')) or null(row.get('createdon'))
    if not when:
        return 0
    return (datetime.datetime.fromisoformat(when[:19]) - EPOCH).days


def clean(text):
    return re.sub(r'\s+', ' ', text.replace('\\t', ' ').replace('\\n', ' ')).strip()


def country_name(text):
    """'UNITED STATES (USA)' -> 'United States', 'KOREA (SOUTH)' -> 'South Korea'."""
    text = clean(text)
    special = {'UNITED STATES (USA)': 'United States', 'KOREA (SOUTH)': 'South Korea', 'KOREA (NORTH)': 'North Korea',
               'UNITED KINGDOM (UK)': 'United Kingdom'}
    if text.upper() in special:
        return special[text.upper()]
    words = []
    for w in re.sub(r'\s*\(.*\)', '', text).split(' '):
        words.append(w.lower() if w.lower() in ('and', 'of', 'the') and words else w[:1].upper() + w[1:].lower())
    return ' '.join(words)


def main(zip_path, out_path):
    element_rows = tables(zip_path, {'element'})['element']
    lookup_of = {int(r['id']): null(r['lookuptable']) for r in element_rows}
    private = {int(r['id']) for r in element_rows if r.get('isprivate') == 't'}
    lookups = sorted({lookup_of[e].lower() for e in ELEMENTS if lookup_of.get(e)})
    t = tables(zip_path, {'wmi', 'wmi_vinschema', 'vinschema', 'pattern', 'make', 'model', 'make_model',
                          'wmi_make', 'manufacturer', 'country', 'vehicletype', 'vehiclespecschema',
                          'vehiclespecschema_model', 'vehiclespecschema_year', 'vspecschemapattern',
                          'vehiclespecpattern', 'enginemodel', 'enginemodelpattern'} | set(lookups))
    name_in = {lk: {r['id']: (country_name if lk == 'country' else clean)(r['name']) for r in t[lk]} for lk in lookups}
    make = {r['id']: clean(r['name']) for r in t['make']}
    make_of_model = {r['modelid']: make.get(r['makeid']) for r in t['make_model']}
    now = datetime.datetime.now()

    wmis = {r['id']: r for r in t['wmi'] if int(r['vehicletypeid'] or 0) in VEHICLE_TYPES
            and null(r['publicavailabilitydate']) and datetime.datetime.fromisoformat(r['publicavailabilitydate'][:19]) <= now}
    qc = {r['id'] for r in t['vinschema'] if r.get('tobeqced') == 't'}
    links = {}
    for r in t['wmi_vinschema']:
        if r['wmiid'] in wmis and r['vinschemaid'] not in qc and (null(r['yearto']) is None or int(r['yearto']) >= FIRST_YEAR):
            links.setdefault(r['wmiid'], []).append((r['vinschemaid'], int(r['yearfrom']), null(r['yearto'])))
    schemas = {s for ls in links.values() for s, _, _ in ls}
    schema_no = {s: i for i, s in enumerate(sorted(schemas, key=int))}

    values, value_no = [], {}

    def value(element, attribute):
        name = ELEMENTS[element]
        lk = lookup_of.get(element)
        if element == 28:
            key = (name, attribute)
            text, extra = clean(name_in['model'].get(attribute, '')) if 'model' in name_in else '', make_of_model.get(attribute)
        elif lk:
            key, text, extra = (name, attribute), name_in[lk.lower()].get(attribute, ''), None
        else:
            key, text, extra = (name, clean(attribute)), clean(attribute), None
        if not text or text.upper() in ('TBD', 'NOT APPLICABLE', 'N/A'):
            return None
        if key not in value_no:
            value_no[key] = len(values)
            values.append((name, text, extra))
        return value_no[key]

    if 'model' not in name_in:
        name_in['model'] = {r['id']: clean(r['name']) for r in t['model']}

    patterns = {}
    for r in t['pattern']:
        e = int(r['elementid'])
        if r['vinschemaid'] not in schemas or e not in ELEMENTS or e in private:
            continue
        v = value(e, r['attributeid'])
        if v is None and '#' not in r['keys']:
            continue
        # A pattern with # takes its value from the VIN itself (the characters where the #s are).
        patterns.setdefault(r['vinschemaid'], []).append((days(r), r['keys'].upper(), int(r['id']), '#' + ELEMENTS[e] if v is None else v))

    # Vehicle specs: details vPIC fills in from the model, year and what the VIN already gave
    # (say the transmission for a model's trim), when the VIN patterns don't say.
    spec_models, spec_years = {}, {}
    for r in t['vehiclespecschema_model']:
        spec_models.setdefault(r['vehiclespecschemaid'], set()).add(r['modelid'])
    for r in t['vehiclespecschema_year']:
        spec_years.setdefault(r['vehiclespecschemaid'], set()).add(int(r['year']))
    spec_schemas = {r['id']: r for r in t['vehiclespecschema'] if int(r['vehicletypeid'] or 0) in VEHICLE_TYPES
                    and r.get('tobeqced') != 't' and r['id'] in spec_models
                    and (r['id'] not in spec_years or max(spec_years[r['id']]) >= FIRST_YEAR)}
    schema_of_spec = {r['id']: r['schemaid'] for r in t['vspecschemapattern'] if r['schemaid'] in spec_schemas}
    spec_rows = {}
    for r in t['vehiclespecpattern']:
        if r['vspecschemapatternid'] in schema_of_spec:
            spec_rows.setdefault(r['vspecschemapatternid'], []).append(r)
    specs = {}
    for pid, rows in spec_rows.items():
        keys = [r for r in rows if r['iskey'] == 't']
        given = [r for r in rows if r['iskey'] != 't' and int(r['elementid']) in ELEMENTS and int(r['elementid']) not in private]
        # A key on a detail the app doesn't keep could never be checked, so that pattern is left out.
        if not keys or not given or any(int(r['elementid']) not in ELEMENTS for r in keys):
            continue
        kv = [(ELEMENTS[int(r['elementid'])], value(int(r['elementid']), r['attributeid'])) for r in keys]
        gv = [(ELEMENTS[int(r['elementid'])], value(int(r['elementid']), r['attributeid']), days(r)) for r in given]
        gv = [g for g in gv if g[1] is not None]
        if any(v is None for _, v in kv) or not gv:
            continue
        specs.setdefault(schema_of_spec[pid], []).append((max(d for _, _, d in gv), sorted(set(kv)), sorted(gv), int(pid)))

    country = {r['id']: country_name(r['name']) for r in t['country']}
    vtype = {r['id']: clean(r['name']) for r in t['vehicletype']}
    maker = {r['id']: clean(r['name']) for r in t['manufacturer']}
    makes_for = {}
    for r in t['wmi_make']:
        makes_for.setdefault(r['wmiid'], []).append(make.get(r['makeid'], ''))

    def light(w):
        # vPIC: passenger cars, MPVs and light trucks (truck type 1) follow the position 7 year rule.
        vt = int(w['vehicletypeid'] or 0)
        return vt in (2, 7) or (vt == 3 and null(w.get('trucktypeid')) == '1')

    out = io.StringIO()
    out.write('# OBD2 Reader offline VIN data 1\n')
    out.write(f'# From NHTSA vPIC ({zip_path.split("/")[-1]}), public domain. Built by tools/vpic/build_subset.py.\n')
    for wid, w in sorted(wmis.items(), key=lambda kv: kv[1]['wmi']):
        if wid not in links:
            continue
        ms = sorted({m for m in makes_for.get(wid, []) if m})
        ls = ','.join(f'{schema_no[s]}:{a}:{b or ""}' for s, a, b in sorted(links[wid], key=lambda x: (x[1], int(x[0]))))
        out.write('\t'.join(['w', w['wmi'].upper(), ms[0] if len(ms) == 1 else '', maker.get(w['manufacturerid'], ''),
                             country.get(w['countryid'], ''), vtype.get(w['vehicletypeid'], ''), ls,
                             '1' if light(w) else '']).rstrip('\t') + '\n')
    for s in sorted(schemas, key=lambda x: schema_no[x]):
        ps = patterns.get(s)
        if not ps:
            continue
        out.write(f's\t{schema_no[s]}\n')
        # vPIC ranks matches newest first, then fewer fixed characters, then by keys and id.
        last = None
        for d, keys, pid, v in sorted(ps, key=lambda p: (-p[0], len(p[1].replace('*', '')), p[1], p[2])):
            # The first pattern carries its day; the rest only how many days older they are (blank: same day).
            step = d if last is None else last - d
            out.write(f'{keys}\t{v}' + (f'\t{step}' if step else '') + '\n')
            last = d
    engine_details = {}
    for r in t['enginemodelpattern']:
        e = int(r['elementid'])
        if e in ELEMENTS and e not in private:
            v = value(e, r['attributeid'])
            if v is not None:
                engine_details.setdefault(r['enginemodelid'], {})[ELEMENTS[e]] = values[v][1]
    engine_count = 0
    for r in sorted(t['enginemodel'], key=lambda r: clean(r['name']).lower()):
        d = engine_details.get(r['id'])
        if d and clean(r['name']):
            out.write('\t'.join(['e', clean(r['name'])] + [x for k in sorted(d) for x in (k, d[k])]) + '\n')
            engine_count += 1
    spec_count = 0
    for sid in sorted(specs, key=int):
        sc = spec_schemas[sid]
        models = sorted({value(28, m) for m in spec_models[sid]} - {None})
        if not models:
            continue
        years = ','.join(str(y) for y in sorted(spec_years.get(sid, ())))
        out.write('\t'.join(['x', vtype.get(sc['vehicletypeid'], ''), ','.join(map(str, models)), years]) + '\n')
        for d, kv, gv, pid in sorted(specs[sid], key=lambda p: (-p[0], p[3])):
            out.write(';'.join(f'{e}={v}' for e, v in kv) + '\t' + ';'.join(f'{e}={v}' for e, v, _ in gv) + f'\t{d}\n')
            spec_count += 1
    for i, (name, text, extra) in enumerate(values):
        out.write('\t'.join(['v', str(i), name, text] + ([extra] if extra else [])) + '\n')
    data = out.getvalue().encode('utf-8')
    with open(out_path, 'wb') as f:
        f.write(gzip.compress(data, 9, mtime=0))
    print(f'{len(wmis)} WMIs, {len(schemas)} schemas, {sum(len(p) for p in patterns.values())} patterns, '
          f'{engine_count} engines, {spec_count} spec patterns, {len(values)} values: {len(data):,} bytes, {len(gzip.compress(data, 9)):,} gzipped')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
