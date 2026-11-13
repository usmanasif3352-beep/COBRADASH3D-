import { FieldValue, Timestamp } from 'firebase-admin/firestore';

function resolveSentinels(obj, existing = {}) {
  if (obj === null || typeof obj !== 'object') return obj;
  if (obj instanceof Timestamp || obj instanceof Date) return obj;
  if (Array.isArray(obj)) return obj.map((v) => resolveSentinels(v));

  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v && v.constructor && v.constructor.name && String(v.constructor.name).includes('ServerTimestamp')) {
      out[k] = Timestamp.now();
    } else if (v === FieldValue.serverTimestamp()) {
      out[k] = Timestamp.now();
    } else if (v && typeof v === 'object' && !Array.isArray(v) && !(v instanceof Timestamp)) {
      out[k] = resolveSentinels(v, existing[k] || {});
    } else {
      out[k] = v;
    }
  }
  return out;
}

class InMemoryDocRef {
  constructor(store, path) {
    this.store = store;
    this.path = path;
    this.id = path.split('/').pop();
  }

  collection(subName) {
    return new InMemoryCollectionRef(this.store, `${this.path}/${subName}`);
  }

  async get() {
    const data = this.store.docs.get(this.path);
    return {
      exists: data !== undefined,
      id: this.id,
      data: () => (data ? structuredClone(data) : undefined)
    };
  }

  async set(data, options = {}) {
    const prev = this.store.docs.get(this.path) || {};
    const resolved = resolveSentinels(data, prev);
    const next = options.merge ? { ...prev, ...resolved } : resolved;
    this.store.docs.set(this.path, next);
  }

  async update(data) {
    const prev = this.store.docs.get(this.path);
    if (!prev) throw new Error(`Document ${this.path} does not exist`);
    const resolved = resolveSentinels(data, prev);
    this.store.docs.set(this.path, { ...prev, ...resolved });
  }
}

class InMemoryQuery {
  constructor(store, prefix, filters = [], order = null, limitVal = 100) {
    this.store = store;
    this.prefix = prefix;
    this.filters = filters;
    this.order = order;
    this.limitVal = limitVal;
  }

  where(field, op, value) {
    return new InMemoryQuery(
      this.store,
      this.prefix,
      [...this.filters, { field, op, value }],
      this.order,
      this.limitVal
    );
  }

  orderBy(field, dir = 'asc') {
    return new InMemoryQuery(this.store, this.prefix, this.filters, { field, dir }, this.limitVal);
  }

  limit(n) {
    return new InMemoryQuery(this.store, this.prefix, this.filters, this.order, n);
  }

  async get() {
    const expectedDepth = this.prefix.split('/').length + 1;
    let items = [];
    for (const [path, data] of this.store.docs.entries()) {
      if (!path.startsWith(`${this.prefix}/`)) continue;
      if (path.split('/').length !== expectedDepth) continue;
      const matches = this.filters.every((f) => {
        if (f.op === '==') return data[f.field] === f.value;
        return true;
      });
      if (matches) {
        items.push({ id: path.split('/').pop(), data: () => structuredClone(data) });
      }
    }

    if (this.order) {
      const { field, dir } = this.order;
      items.sort((a, b) => {
        const av = a.data()[field] ?? 0;
        const bv = b.data()[field] ?? 0;
        const cmp = av < bv ? -1 : av > bv ? 1 : 0;
        return dir === 'desc' ? -cmp : cmp;
      });
    }

    items = items.slice(0, this.limitVal);
    return { docs: items };
  }
}

class InMemoryCollectionRef extends InMemoryQuery {
  constructor(store, path) {
    super(store, path);
  }

  document(id = null) {
    const docId = id || `auto_${++this.store.autoId}`;
    return new InMemoryDocRef(this.store, `${this.prefix}/${docId}`);
  }
}

export class TestFirestoreDb {
  constructor() {
    this.docs = new Map();
    this.autoId = 0;
  }

  collection(name) {
    return new InMemoryCollectionRef(this, name);
  }

  async runTransaction(fn) {
    const tx = {
      get: (ref) => ref.get(),
      set: (ref, data, opts) => ref.set(data, opts),
      update: (ref, data) => ref.update(data)
    };
    return fn(tx);
  }
}

export class TestFirebaseAuth {
  constructor() {
    this.tokens = new Map([
      ['token-alice', { uid: 'uid_alice', name: 'AliceViper', email: 'alice@example.com', firebase: { sign_in_provider: 'google.com' } }],
      ['token-bob', { uid: 'uid_bob', name: 'BobCobra', email: 'bob@example.com', firebase: { sign_in_provider: 'google.com' } }],
      ['token-anon', { uid: 'uid_anon', firebase: { sign_in_provider: 'anonymous' } }],
      ['token-admin', { uid: 'uid_admin', name: 'AdminCobra', admin: true, firebase: { sign_in_provider: 'google.com' } }]
    ]);
  }

  async verifyIdToken(token) {
    const decoded = this.tokens.get(token);
    if (!decoded) throw new Error('Invalid test Firebase token');
    return decoded;
  }
}
