import { useState, useEffect, useCallback, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import {
  Film, Type, Palette, ChevronLeft, Trash2, Sparkles,
  RefreshCw, Loader2, Scissors, Eye, Share2, Download,
  Plus, Check, Play, Globe, Hash, Zap, Search,
  X, TrendingUp, MessageSquare, AlertTriangle, CheckCircle2
} from 'lucide-react';

const B = window.tasklet;

// ═══════════════════════════════════════════════════════
// TYPES
// ═══════════════════════════════════════════════════════
interface Project {
  id: string; name: string; input_path: string; output_path: string;
  thumbnail: string; caption: string; font: string; text_color: string;
  highlight_color: string; font_size: string; subtitle_style: string;
  auto_cut: number | string; status: string; created_at: string;
  language: string; keywords: string; content_mood: string; viral_sensitivity: string;
}

type View = 'dash' | 'edit' | 'result';

// Helper: SQLite returns numbers as strings sometimes
const numVal = (v: number | string): number => typeof v === 'string' ? parseInt(v, 10) || 0 : v;
const isAutoCut = (p: Project): boolean => numVal(p.auto_cut) === 1;

// Helper: Ensure file paths get avfs:// prefix, but leave data: and http: URLs untouched
const toAvfs = (path: string): string => {
  if (!path) return '';
  if (path.startsWith('avfs://') || path.startsWith('data:') || path.startsWith('http')) return path;
  return `avfs://${path}`;
};

// ═══════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════
const FONTS = [
  { id: 'Anton', label: 'Anton', css: "'Anton', sans-serif" },
  { id: 'Bebas Neue', label: 'Bebas Neue', css: "'Bebas Neue', sans-serif" },
  { id: 'Montserrat', label: 'Montserrat Black', css: "'Montserrat', sans-serif" },
  { id: 'Oswald', label: 'Oswald Bold', css: "'Oswald', sans-serif" },
  { id: 'Poppins', label: 'Poppins ExtraBold', css: "'Poppins', sans-serif" },
  { id: 'Bangers', label: 'Bangers', css: "'Bangers', cursive" },
];

const TEXT_COLORS = [
  { id: '#FFFFFF', n: 'Weiß' }, { id: '#FFD700', n: 'Gelb' },
  { id: '#00FFFF', n: 'Cyan' }, { id: '#39FF14', n: 'Grün' },
  { id: '#FF3333', n: 'Rot' }, { id: '#FF6B00', n: 'Orange' },
  { id: '#FF1493', n: 'Pink' }, { id: '#A855F7', n: 'Lila' },
];

const HL_COLORS = [
  { id: '#39FF14', n: 'Neon-Grün' }, { id: '#FFD700', n: 'Gelb' },
  { id: '#00FFFF', n: 'Cyan' }, { id: '#FF3333', n: 'Rot' },
  { id: '#FF6B00', n: 'Orange' }, { id: '#FF1493', n: 'Hot Pink' },
];

const SIZES = [
  { id: 'Klein', px: 14, v: '36px' },
  { id: 'Mittel', px: 17, v: '48px' },
  { id: 'Groß', px: 21, v: '64px' },
  { id: 'XL', px: 26, v: '80px' },
];

const STYLES = [
  { id: 'karaoke', label: 'Karaoke', desc: 'Wort für Wort', icon: '🎤' },
  { id: 'classic', label: 'Klassisch', desc: 'Weiß + Schatten', icon: '📝' },
  { id: 'neon', label: 'Neon Glow', desc: 'Leuchtend', icon: '✨' },
  { id: 'box', label: 'Box', desc: 'Farbige Box', icon: '🔲' },
  { id: 'outline', label: 'Outline', desc: 'Fetter Umriss', icon: '🔤' },
];

const LANGUAGES = [
  { id: 'de', label: 'Deutsch', flag: '🇩🇪' },
  { id: 'en', label: 'English', flag: '🇬🇧' },
  { id: 'tr', label: 'Türkçe', flag: '🇹🇷' },
  { id: 'ar', label: 'العربية', flag: '🇸🇦' },
  { id: 'es', label: 'Español', flag: '🇪🇸' },
  { id: 'fr', label: 'Français', flag: '🇫🇷' },
  { id: 'auto', label: 'Auto-Erkennung', flag: '🌍' },
];

const MOODS = [
  { id: 'all', label: 'Alle Momente', desc: 'Keine Einschränkung', icon: '🎯', color: '#A855F7' },
  { id: 'controversial', label: 'Kontrovers', desc: 'Provokante Aussagen', icon: '🔥', color: '#FF3333' },
  { id: 'emotional', label: 'Emotional', desc: 'Berührende Momente', icon: '😢', color: '#3B82F6' },
  { id: 'funny', label: 'Lustig', desc: 'Witzige Stellen', icon: '😂', color: '#FFD700' },
  { id: 'realtalk', label: 'Realtalk', desc: 'Ehrliche Wahrheiten', icon: '💯', color: '#39FF14' },
  { id: 'drama', label: 'Drama', desc: 'Spannende Konflikte', icon: '🎭', color: '#FF6B00' },
  { id: 'motivation', label: 'Motivation', desc: 'Inspirierende Worte', icon: '💪', color: '#00FFFF' },
  { id: 'skandal', label: 'Skandal', desc: 'Schockierende Enthüllungen', icon: '😱', color: '#FF1493' },
];

const SENSITIVITIES = [
  { id: 'low', label: 'Wenig', desc: 'Nur die krassesten Stellen', clips: '1–3 Clips', icon: '🎯' },
  { id: 'medium', label: 'Mittel', desc: 'Gute Balance', clips: '3–6 Clips', icon: '⚡' },
  { id: 'high', label: 'Alles', desc: 'Maximale Clip-Ausbeute', clips: '6–12 Clips', icon: '🔥' },
];

const KEYWORD_SUGGESTIONS: Record<string, string[]> = {
  controversial: ['Skandal', 'Wahrheit', 'Lüge', 'verboten', 'geheim', 'Manipulation'],
  emotional: ['traurig', 'Tränen', 'Herz', 'vermissen', 'Liebe', 'Angst'],
  funny: ['lustig', 'Witz', 'lachen', 'krass', 'Fail', 'Bruh'],
  realtalk: ['ehrlich', 'Wahrheit', 'Gesellschaft', 'Problem', 'Realität', 'System'],
  drama: ['Streit', 'Konflikt', 'Beef', 'Angriff', 'Eskalation', 'Stress'],
  motivation: ['Erfolg', 'Disziplin', 'Ziel', 'Traum', 'niemals aufgeben', 'Kraft'],
  skandal: ['Skandal', 'Enthüllung', 'Korruption', 'Betrug', 'aufgedeckt', 'Schock'],
};

// ═══════════════════════════════════════════════════════
// DB
// ═══════════════════════════════════════════════════════
const initDB = () => B.sqlExec(`CREATE TABLE IF NOT EXISTS vc_projects (
  id TEXT PRIMARY KEY, name TEXT NOT NULL, input_path TEXT DEFAULT '',
  output_path TEXT DEFAULT '', thumbnail TEXT DEFAULT '', caption TEXT DEFAULT '',
  font TEXT DEFAULT 'Anton', text_color TEXT DEFAULT '#FFFFFF',
  highlight_color TEXT DEFAULT '#39FF14', font_size TEXT DEFAULT 'Groß',
  subtitle_style TEXT DEFAULT 'karaoke',
  auto_cut INTEGER DEFAULT 1, status TEXT DEFAULT 'new', created_at TEXT DEFAULT '',
  language TEXT DEFAULT 'de', keywords TEXT DEFAULT '', content_mood TEXT DEFAULT 'all',
  viral_sensitivity TEXT DEFAULT 'medium'
)`);

const esc = (s: string) => s.replace(/'/g, "''");

const loadAll = async (): Promise<Project[]> => {
  const rows = await B.sqlQuery('SELECT * FROM vc_projects ORDER BY created_at DESC');
  return rows as unknown as Project[];
};

const updateField = (id: string, key: string, val: string | number) =>
  B.sqlExec(`UPDATE vc_projects SET ${key}='${esc(String(val))}' WHERE id='${esc(id)}'`);

// ═══════════════════════════════════════════════════════
// TOAST NOTIFICATION
// ═══════════════════════════════════════════════════════
function Toast({ message, type, onClose }: { message: string; type: 'success' | 'error' | 'info'; onClose: () => void }) {
  const cbRef = useRef(onClose);
  cbRef.current = onClose;

  useEffect(() => {
    const t = setTimeout(() => cbRef.current(), 3500);
    return () => clearTimeout(t);
  }, []);

  const colors = {
    success: 'bg-success text-success-content',
    error: 'bg-error text-error-content',
    info: 'bg-info text-info-content',
  };

  return (
    <div className={`fixed top-4 left-4 right-4 z-50 ${colors[type]} rounded-xl p-3 text-sm font-bold shadow-2xl flex items-center gap-2 toast-enter`}>
      {type === 'success' && <CheckCircle2 size={16} />}
      {type === 'error' && <AlertTriangle size={16} />}
      {type === 'info' && <Sparkles size={16} />}
      {message}
      <button className="ml-auto opacity-60 hover:opacity-100" onClick={onClose}><X size={14} /></button>
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// CONFIRM DIALOG
// ═══════════════════════════════════════════════════════
function ConfirmDialog({ title, message, onConfirm, onCancel }: {
  title: string; message: string; onConfirm: () => void; onCancel: () => void;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="bg-base-200 rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-white/10">
        <div className="flex items-center gap-3 mb-3">
          <div className="w-10 h-10 rounded-full bg-error/20 flex items-center justify-center">
            <AlertTriangle size={20} className="text-error" />
          </div>
          <h3 className="font-bold text-lg">{title}</h3>
        </div>
        <p className="text-sm opacity-70 mb-6">{message}</p>
        <div className="flex gap-3">
          <button className="btn btn-ghost flex-1 rounded-xl" onClick={onCancel}>Abbrechen</button>
          <button className="btn btn-error flex-1 rounded-xl" onClick={onConfirm}>Löschen</button>
        </div>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// SUBTITLE PREVIEW
// ═══════════════════════════════════════════════════════
function SubPrev({ p }: { p: Project }) {
  const font = FONTS.find(f => f.id === p.font);
  const sz = SIZES.find(s => s.id === p.font_size);
  const base: React.CSSProperties = {
    fontFamily: font?.css || "'Anton', sans-serif",
    fontSize: sz?.px || 21, fontWeight: 900,
    textTransform: 'uppercase', textShadow: '2px 2px 4px rgba(0,0,0,.8)',
    letterSpacing: '0.5px',
  };

  if (p.subtitle_style === 'karaoke') return (
    <div style={base}>
      <span style={{ color: p.text_color }}>DAS IST </span>
      <span style={{ color: p.highlight_color, transform: 'scale(1.1)', display: 'inline-block' }}>EIN</span>
      <span style={{ color: p.text_color }}> BEISPIEL</span>
    </div>
  );
  if (p.subtitle_style === 'neon') return (
    <div style={{ ...base, color: p.text_color,
      textShadow: `0 0 7px ${p.text_color}, 0 0 15px ${p.text_color}, 0 0 30px ${p.text_color}` }}>
      DAS IST EIN BEISPIEL
    </div>
  );
  if (p.subtitle_style === 'box') return (
    <span style={{ ...base, color: '#fff', background: p.highlight_color,
      padding: '4px 14px', borderRadius: 8, display: 'inline-block' }}>
      DAS IST EIN BEISPIEL
    </span>
  );
  if (p.subtitle_style === 'outline') return (
    <div style={{ ...base, color: p.text_color,
      WebkitTextStroke: '2px #000', paintOrder: 'stroke fill' as any }}>
      DAS IST EIN BEISPIEL
    </div>
  );
  return <div style={{ ...base, color: p.text_color }}>DAS IST EIN BEISPIEL</div>;
}

// ═══════════════════════════════════════════════════════
// KEYWORD INPUT COMPONENT
// ═══════════════════════════════════════════════════════
function KeywordInput({ keywords, onChange, mood }: {
  keywords: string; onChange: (v: string) => void; mood: string;
}) {
  const [input, setInput] = useState('');
  const tags = keywords ? keywords.split(',').filter(Boolean) : [];

  const addTag = (tag: string) => {
    const t = tag.trim();
    if (!t || tags.includes(t)) return;
    onChange([...tags, t].join(','));
    setInput('');
  };

  const removeTag = (tag: string) => {
    onChange(tags.filter(t => t !== tag).join(','));
  };

  const suggestions = KEYWORD_SUGGESTIONS[mood] || [];
  const unusedSuggestions = suggestions.filter(s => !tags.includes(s));

  return (
    <div className="space-y-3">
      {/* Input field */}
      <div className="flex gap-2">
        <div className="relative flex-1">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 opacity-30" />
          <input
            type="text" className="input input-bordered input-sm w-full pl-9 bg-base-200/30 rounded-xl text-xs"
            placeholder="Schlüsselwort eingeben..."
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') { e.preventDefault(); addTag(input); } }}
          />
        </div>
        <button className="btn btn-primary btn-sm rounded-xl px-4"
          onClick={() => addTag(input)} disabled={!input.trim()}>
          <Plus size={14} />
        </button>
      </div>

      {/* Current tags */}
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {tags.map(tag => (
            <span key={tag} className="badge badge-primary gap-1 badge-sm py-2.5 px-3 font-semibold">
              <Hash size={10} /> {tag}
              <button onClick={() => removeTag(tag)} className="ml-1 hover:text-error">
                <X size={10} />
              </button>
            </span>
          ))}
        </div>
      )}

      {/* Suggestions */}
      {unusedSuggestions.length > 0 && (
        <div>
          <p className="text-[10px] opacity-40 mb-2 flex items-center gap-1">
            <Sparkles size={10} /> Vorschläge basierend auf Thema
          </p>
          <div className="flex flex-wrap gap-1.5">
            {unusedSuggestions.map(s => (
              <button key={s} className="badge badge-ghost badge-sm py-2 px-2.5 cursor-pointer hover:badge-primary transition-all text-[10px]"
                onClick={() => addTag(s)}>
                + {s}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// DASHBOARD
// ═══════════════════════════════════════════════════════
function Dash({ projects, onSelect, onNew, creatingNew }: {
  projects: Project[]; onSelect: (p: Project) => void; onNew: () => void; creatingNew: boolean;
}) {
  return (
    <div className="p-4 pb-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-extrabold flex items-center gap-2">
            <Sparkles size={26} className="text-primary" /> ViralClip AI
          </h1>
          <p className="text-xs opacity-50 mt-1">v4.2 · KI Video-Editor für virale Clips</p>
        </div>
        <button className="btn btn-primary btn-sm gap-2 rounded-xl" onClick={onNew} disabled={creatingNew}>
          {creatingNew ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} />}
          {creatingNew ? 'Wird erstellt...' : 'Neues Video'}
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-3 mb-6">
        <div className="gcard p-3 text-center">
          <div className="text-xl font-bold text-primary">{projects.length}</div>
          <div className="text-[10px] opacity-50 uppercase tracking-wider">Projekte</div>
        </div>
        <div className="gcard p-3 text-center">
          <div className="text-xl font-bold text-success">{projects.filter(p => p.status === 'done').length}</div>
          <div className="text-[10px] opacity-50 uppercase tracking-wider">Fertig</div>
        </div>
        <div className="gcard p-3 text-center">
          <div className="text-xl font-bold text-warning">{projects.filter(p => p.status === 'processing').length}</div>
          <div className="text-[10px] opacity-50 uppercase tracking-wider">In Arbeit</div>
        </div>
      </div>

      {/* Project list */}
      {projects.length === 0 ? (
        <div className="text-center py-16 opacity-40">
          <Film size={56} className="mx-auto mb-4" />
          <p className="text-lg font-bold">Noch keine Projekte</p>
          <p className="text-sm mt-2">Klicke oben auf &quot;Neues Video&quot; um loszulegen</p>
        </div>
      ) : (
        <div className="space-y-3">
          {projects.map(p => {
            const mood = MOODS.find(m => m.id === p.content_mood);
            const lang = LANGUAGES.find(l => l.id === p.language);
            return (
              <div key={p.id} className="gcard hover:bg-white/5 cursor-pointer transition-all p-3 flex items-center gap-4"
                onClick={() => onSelect(p)}>
                <div className="w-14 h-24 bg-black rounded-lg flex items-center justify-center text-2xl flex-shrink-0 overflow-hidden">
                  {p.thumbnail ? <img src={toAvfs(p.thumbnail)} className="w-full h-full object-cover" alt="" /> : '🎬'}
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="font-bold truncate text-sm">{p.name}</h3>
                  <p className="text-[10px] opacity-40 mt-0.5">{p.created_at}</p>
                  <div className="mt-2 flex items-center gap-2 flex-wrap">
                    {p.status === 'new' && <span className="badge badge-ghost badge-xs">Neu</span>}
                    {p.status === 'configuring' && <span className="badge badge-info badge-xs">Bearbeitung</span>}
                    {p.status === 'processing' && <span className="badge badge-warning badge-xs gap-1"><Loader2 size={10} className="animate-spin" />Verarbeitung</span>}
                    {p.status === 'done' && <span className="badge badge-success badge-xs gap-1"><Check size={10} />Fertig</span>}
                    {p.status === 'error' && <span className="badge badge-error badge-xs">Fehler</span>}
                    {lang && <span className="badge badge-ghost badge-xs">{lang.flag}</span>}
                    {mood && mood.id !== 'all' && <span className="badge badge-ghost badge-xs">{mood.icon} {mood.label}</span>}
                  </div>
                </div>
                <ChevronLeft size={18} className="rotate-180 opacity-30 flex-shrink-0" />
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// EDITOR
// ═══════════════════════════════════════════════════════
function Editor({ p, onBack, onProcess, onChange, onDelete, busy }: {
  p: Project; onBack: () => void; onProcess: () => void;
  onChange: (k: string, v: string | number) => void; onDelete: () => void; busy: boolean;
}) {
  const [tab, setTab] = useState<'caption' | 'subtitle' | 'filter' | 'settings'>('caption');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  return (
    <div className="pb-24">
      {/* Delete Confirmation */}
      {showDeleteConfirm && (
        <ConfirmDialog
          title="Projekt löschen?"
          message={`"${p.name}" wird unwiderruflich gelöscht. Diese Aktion kann nicht rückgängig gemacht werden.`}
          onConfirm={() => { setShowDeleteConfirm(false); onDelete(); }}
          onCancel={() => setShowDeleteConfirm(false)}
        />
      )}

      {/* Header */}
      <div className="sticky top-0 z-20 bg-base-100/95 backdrop-blur-md p-3 border-b border-white/5">
        <div className="flex items-center gap-2">
          <button className="btn btn-ghost btn-sm btn-circle" onClick={onBack}><ChevronLeft size={20} /></button>
          <div className="flex-1 min-w-0">
            <h2 className="font-bold truncate text-sm">{p.name}</h2>
            <p className="text-[10px] opacity-40">Projekt bearbeiten</p>
          </div>
          <button className="btn btn-ghost btn-sm btn-circle text-error/60" onClick={() => setShowDeleteConfirm(true)}>
            <Trash2 size={15} />
          </button>
        </div>
      </div>

      {/* Error Banner */}
      {p.status === 'error' && (
        <div className="mx-4 mt-3 p-3 bg-error/15 border border-error/30 rounded-xl flex items-center gap-3">
          <AlertTriangle size={18} className="text-error flex-shrink-0" />
          <div>
            <p className="font-bold text-sm text-error">Verarbeitungsfehler</p>
            <p className="text-[10px] opacity-60">Bitte ändere die Einstellungen und versuche es erneut.</p>
          </div>
        </div>
      )}

      {/* Live Preview */}
      <div className="p-4">
        <div className="vf mx-auto" style={{ maxWidth: 240 }}>
          <div className="w-full h-full bg-gradient-to-b from-base-300 to-base-100 flex items-center justify-center">
            {p.thumbnail
              ? <img src={toAvfs(p.thumbnail)} className="w-full h-full object-cover" alt="" />
              : <Film size={40} className="opacity-20" />}
          </div>
          {p.caption && <div className="cap-box">{p.caption}</div>}
          <div className="sub-box"><SubPrev p={p} /></div>
        </div>
        <p className="text-[10px] opacity-30 text-center mt-2">Live-Vorschau · 9:16</p>
      </div>

      {/* Tabs */}
      <div className="px-4">
        <div className="flex gap-1 bg-base-200/50 p-1 rounded-xl mb-5">
          {([['caption', '✏️', 'Caption'], ['subtitle', '🎤', 'Untertitel'], ['filter', '🔍', 'Content'], ['settings', '⚙️', 'Settings']] as const).map(([id, icon, label]) => (
            <button key={id}
              className={`flex-1 py-2 rounded-lg text-xs font-bold transition-all ${tab === id ? 'bg-primary text-primary-content shadow-lg' : 'opacity-60 hover:opacity-80'}`}
              onClick={() => setTab(id)}>
              {icon} {label}
            </button>
          ))}
        </div>

        {/* ── CAPTION TAB ─── */}
        {tab === 'caption' && (
          <div className="space-y-4">
            <div>
              <label className="text-xs font-bold opacity-60 mb-2 block">HOOK-CAPTION (oben im Video)</label>
              <textarea className="textarea textarea-bordered w-full bg-base-200/30 rounded-xl text-sm"
                rows={3} placeholder="z.B. Superstar-Allüren: Warum sein Erfolg ihn unbeliebt macht"
                value={p.caption} onChange={e => onChange('caption', e.target.value)} />
              <p className="text-[10px] opacity-30 mt-1">Weißer Kasten mit fetter schwarzer Schrift · oben im Video</p>
            </div>
            <div className="gcard p-3 flex items-start gap-2">
              <Sparkles size={14} className="text-primary mt-0.5 flex-shrink-0" />
              <p className="text-xs opacity-70">Kurz &amp; knackig = mehr Views! Die Caption sollte neugierig machen und zum Weiterschauen animieren.</p>
            </div>
          </div>
        )}

        {/* ── SUBTITLE TAB ─── */}
        {tab === 'subtitle' && (
          <div className="space-y-6">
            {/* Font */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2"><Type size={14} /> SCHRIFTART</h3>
              <div className="space-y-2">
                {FONTS.map(f => (
                  <div key={f.id} className={`sel ${p.font === f.id ? 'on' : ''}`}
                    onClick={() => onChange('font', f.id)}>
                    <span style={{ fontFamily: f.css, fontSize: 18 }}>{f.label}</span>
                    {p.font === f.id && <Check size={14} className="float-right mt-1 text-primary" />}
                  </div>
                ))}
              </div>
            </div>

            {/* Text Color */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2"><Palette size={14} /> TEXTFARBE</h3>
              <div className="flex flex-wrap gap-3">
                {TEXT_COLORS.map(c => (
                  <div key={c.id} className={`cdot ${p.text_color === c.id ? 'on' : ''}`}
                    style={{ background: c.id }} onClick={() => onChange('text_color', c.id)} title={c.n} />
                ))}
              </div>
            </div>

            {/* Highlight Color */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3">🎯 HIGHLIGHT-FARBE (aktives Wort)</h3>
              <div className="flex flex-wrap gap-3">
                {HL_COLORS.map(c => (
                  <div key={c.id} className={`cdot ${p.highlight_color === c.id ? 'on' : ''}`}
                    style={{ background: c.id }} onClick={() => onChange('highlight_color', c.id)} title={c.n} />
                ))}
              </div>
            </div>

            {/* Size */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3">📐 GRÖSSE</h3>
              <div className="flex gap-2">
                {SIZES.map(s => (
                  <div key={s.id} className={`szopt ${p.font_size === s.id ? 'on' : ''}`}
                    onClick={() => onChange('font_size', s.id)}>
                    <div style={{ fontSize: s.px, fontWeight: 800 }}>Aa</div>
                    <div className="text-[10px] mt-1 opacity-50">{s.id}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Style */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3">🎬 UNTERTITEL-STIL</h3>
              <div className="grid grid-cols-2 gap-2">
                {STYLES.map(s => (
                  <div key={s.id} className={`stcard ${p.subtitle_style === s.id ? 'on' : ''}`}
                    onClick={() => onChange('subtitle_style', s.id)}>
                    <div className="text-2xl mb-1">{s.icon}</div>
                    <div className="font-bold text-xs">{s.label}</div>
                    <div className="text-[10px] opacity-50">{s.desc}</div>
                  </div>
                ))}
              </div>
            </div>

            {/* Live Subtitle Preview */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2"><Eye size={14} /> VORSCHAU</h3>
              <div className="bg-black rounded-xl p-6 text-center"><SubPrev p={p} /></div>
            </div>
          </div>
        )}

        {/* ── CONTENT FILTER TAB ─── */}
        {tab === 'filter' && (
          <div className="space-y-6">
            {/* Language */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2">
                <Globe size={14} /> SPRACHE DES VIDEOS
              </h3>
              <p className="text-[10px] opacity-40 mb-3">Sprache für KI-Transkription und Keyword-Erkennung</p>
              <div className="grid grid-cols-2 gap-2">
                {LANGUAGES.map(l => (
                  <div key={l.id}
                    className={`gcard p-3 cursor-pointer transition-all flex items-center gap-3 ${p.language === l.id ? 'ring-2 ring-primary bg-primary/10' : 'hover:bg-white/5'}`}
                    onClick={() => onChange('language', l.id)}>
                    <span className="text-xl">{l.flag}</span>
                    <div>
                      <div className="font-bold text-xs">{l.label}</div>
                    </div>
                    {p.language === l.id && <Check size={14} className="ml-auto text-primary" />}
                  </div>
                ))}
              </div>
            </div>

            {/* Content Mood / Theme */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2">
                <TrendingUp size={14} /> THEMA / STIMMUNG
              </h3>
              <p className="text-[10px] opacity-40 mb-3">KI sucht gezielt nach Momenten mit diesem Vibe</p>
              <div className="grid grid-cols-2 gap-2">
                {MOODS.map(m => (
                  <div key={m.id}
                    className={`gcard p-3 cursor-pointer transition-all text-center ${p.content_mood === m.id ? 'ring-2 ring-primary bg-primary/10' : 'hover:bg-white/5'}`}
                    onClick={() => onChange('content_mood', m.id)}>
                    <div className="text-2xl mb-1">{m.icon}</div>
                    <div className="font-bold text-xs">{m.label}</div>
                    <div className="text-[10px] opacity-50">{m.desc}</div>
                    {p.content_mood === m.id && (
                      <div className="mt-1"><Check size={12} className="text-primary mx-auto" /></div>
                    )}
                  </div>
                ))}
              </div>
            </div>

            {/* Keywords */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2">
                <Hash size={14} /> SCHLÜSSELWÖRTER
              </h3>
              <p className="text-[10px] opacity-40 mb-3">
                KI schneidet bevorzugt Stellen, in denen diese Wörter vorkommen
              </p>
              <KeywordInput
                keywords={p.keywords}
                onChange={v => onChange('keywords', v)}
                mood={p.content_mood}
              />
            </div>

            {/* Viral Sensitivity */}
            <div>
              <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2">
                <Zap size={14} /> VIRAL-EMPFINDLICHKEIT
              </h3>
              <p className="text-[10px] opacity-40 mb-3">Wie viele Clips soll die KI finden?</p>
              <div className="space-y-2">
                {SENSITIVITIES.map(s => (
                  <div key={s.id}
                    className={`gcard p-3 cursor-pointer transition-all flex items-center gap-3 ${p.viral_sensitivity === s.id ? 'ring-2 ring-primary bg-primary/10' : 'hover:bg-white/5'}`}
                    onClick={() => onChange('viral_sensitivity', s.id)}>
                    <span className="text-xl">{s.icon}</span>
                    <div className="flex-1">
                      <div className="font-bold text-xs">{s.label}</div>
                      <div className="text-[10px] opacity-50">{s.desc}</div>
                    </div>
                    <span className="badge badge-ghost badge-xs">{s.clips}</span>
                    {p.viral_sensitivity === s.id && <Check size={14} className="text-primary" />}
                  </div>
                ))}
              </div>
            </div>

            {/* Active filter summary */}
            {(() => {
              const lang = LANGUAGES.find(l => l.id === p.language);
              const mood = MOODS.find(m => m.id === p.content_mood);
              const sens = SENSITIVITIES.find(s => s.id === p.viral_sensitivity);
              return (
                <div className="gcard p-4">
                  <h3 className="text-xs font-bold opacity-60 mb-3 flex items-center gap-2">
                    <MessageSquare size={14} /> FILTER-ZUSAMMENFASSUNG
                  </h3>
                  <div className="space-y-2 text-xs">
                    <div className="flex justify-between">
                      <span className="opacity-50">Sprache</span>
                      <span className="font-semibold">{lang?.flag} {lang?.label}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="opacity-50">Thema</span>
                      <span className="font-semibold">{mood?.icon} {mood?.label}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="opacity-50">Empfindlichkeit</span>
                      <span className="font-semibold">{sens?.label}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="opacity-50">Keywords</span>
                      <span className="font-semibold">{p.keywords ? p.keywords.split(',').length : 0} aktiv</span>
                    </div>
                  </div>
                </div>
              );
            })()}
          </div>
        )}

        {/* ── SETTINGS TAB ─── */}
        {tab === 'settings' && (
          <div className="space-y-4">
            <div className="gcard p-4">
              <label className="flex items-center justify-between cursor-pointer">
                <div>
                  <div className="font-bold text-sm flex items-center gap-2"><Scissors size={16} /> Auto-Cut</div>
                  <p className="text-[10px] opacity-40 mt-1">Nahaufnahme auf Gesicht beim Reden,<br/>Zoom-Out bei Pausen (wie OpusClip)</p>
                </div>
                <input type="checkbox" className="toggle toggle-primary toggle-sm"
                  checked={isAutoCut(p)} onChange={e => onChange('auto_cut', e.target.checked ? 1 : 0)} />
              </label>
            </div>
            <div className="gcard p-4">
              <div className="font-bold text-sm flex items-center gap-2 mb-1">📐 Format</div>
              <p className="text-[10px] opacity-40">9:16 Hochformat (TikTok / Reels / Shorts)</p>
              <div className="badge badge-primary badge-sm mt-2">9:16 fest</div>
            </div>
            <div className="gcard p-4">
              <div className="font-bold text-sm flex items-center gap-2 mb-1">🔊 Audio</div>
              <p className="text-[10px] opacity-40">Original-Ton wird beibehalten</p>
              <div className="badge badge-success badge-sm mt-2">Ton aktiv</div>
            </div>
            <div className="gcard p-4">
              <div className="font-bold text-sm flex items-center gap-2 mb-1">🤖 KI-Transkription</div>
              <p className="text-[10px] opacity-40">Automatische Spracherkennung für Untertitel via Whisper AI</p>
              <div className="badge badge-info badge-sm mt-2">Automatisch</div>
            </div>
          </div>
        )}
      </div>

      {/* Process Button */}
      <div className="fixed bottom-0 left-0 right-0 p-4 bg-base-100/95 backdrop-blur-md border-t border-white/5 z-30">
        <button className="btn btn-primary w-full gap-2 rounded-xl font-bold shadow-lg shadow-primary/20"
          onClick={onProcess} disabled={busy || !p.input_path || p.status === 'processing' || p.status === 'done'}>
          {(busy || p.status === 'processing')
            ? <><Loader2 size={18} className="animate-spin" /> Wird verarbeitet...</>
            : !p.input_path
            ? <>Zuerst ein Video hochladen</>
            : <><Sparkles size={18} /> KI-Video erstellen</>}
        </button>
      </div>
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// RESULT
// ═══════════════════════════════════════════════════════
function Result({ p, onBack, onReprocess, onToast }: {
  p: Project; onBack: () => void; onReprocess: () => void;
  onToast: (msg: string, type: 'success' | 'error' | 'info') => void;
}) {
  const [uploadingTo, setUploadingTo] = useState<string | null>(null);
  const uploadTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const videoUrl = p.output_path ? toAvfs(p.output_path) : null;

  // Cleanup timeout on unmount
  useEffect(() => () => { if (uploadTimerRef.current) clearTimeout(uploadTimerRef.current); }, []);

  const handleSocialUpload = async (platform: string, label: string) => {
    setUploadingTo(platform);
    onToast(`Upload zu ${label} gestartet...`, 'info');
    try {
      await B.sendMessageToAgent(`Upload project ${p.id} to ${platform}. Video: ${p.output_path}`);
    } catch {
      onToast(`Upload zu ${label} fehlgeschlagen`, 'error');
    }
    // Reset after 3s since we can't track actual completion from here
    if (uploadTimerRef.current) clearTimeout(uploadTimerRef.current);
    uploadTimerRef.current = setTimeout(() => setUploadingTo(null), 3000);
  };

  return (
    <div className="pb-8">
      {/* Header */}
      <div className="sticky top-0 z-20 bg-base-100/95 backdrop-blur-md p-3 border-b border-white/5">
        <div className="flex items-center gap-2">
          <button className="btn btn-ghost btn-sm btn-circle" onClick={onBack}><ChevronLeft size={20} /></button>
          <h2 className="font-bold flex-1 text-sm">Fertiges Video ✅</h2>
          <button className="btn btn-ghost btn-xs gap-1" onClick={onReprocess}>
            <RefreshCw size={12} /> Neu
          </button>
        </div>
      </div>

      {/* Video Player */}
      <div className="p-4">
        <div className="vf mx-auto" style={{ maxWidth: 300 }}>
          {videoUrl ? (
            <video src={videoUrl} controls playsInline className="w-full h-full object-contain" />
          ) : p.thumbnail ? (
            <div className="w-full h-full relative">
              <img src={toAvfs(p.thumbnail)} className="w-full h-full object-cover" alt="" />
              <div className="absolute inset-0 flex items-center justify-center bg-black/30">
                <Play size={48} className="text-white/80" />
              </div>
            </div>
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <p className="opacity-40 text-sm">Video wird geladen...</p>
            </div>
          )}
        </div>
      </div>

      {/* Download */}
      {p.output_path && (
        <div className="px-4 mb-4">
          <a href={toAvfs(p.output_path)} download className="btn btn-outline w-full gap-2 rounded-xl btn-sm">
            <Download size={16} /> Video herunterladen
          </a>
        </div>
      )}

      {/* ── SOCIAL MEDIA UPLOAD (direkt unter dem Video) ─── */}
      <div className="px-4">
        <div className="gcard p-4">
          <h3 className="font-bold text-sm mb-4 flex items-center gap-2">
            <Share2 size={16} className="text-primary" /> Auf Social Media teilen
          </h3>
          <div className="space-y-3">
            <button className="sbtn tt" disabled={uploadingTo === 'TikTok'}
              onClick={() => handleSocialUpload('TikTok', 'TikTok')}>
              {uploadingTo === 'TikTok'
                ? <Loader2 size={20} className="animate-spin" />
                : <span className="text-xl">🎵</span>}
              TikTok hochladen
            </button>
            <button className="sbtn yt" disabled={uploadingTo === 'YouTube Shorts'}
              onClick={() => handleSocialUpload('YouTube Shorts', 'YouTube Shorts')}>
              {uploadingTo === 'YouTube Shorts'
                ? <Loader2 size={20} className="animate-spin" />
                : <span className="text-xl">▶️</span>}
              YouTube Shorts hochladen
            </button>
            <button className="sbtn ig" disabled={uploadingTo === 'Instagram Reels'}
              onClick={() => handleSocialUpload('Instagram Reels', 'Instagram Reels')}>
              {uploadingTo === 'Instagram Reels'
                ? <Loader2 size={20} className="animate-spin" />
                : <span className="text-xl">📸</span>}
              Instagram Reels hochladen
            </button>
          </div>
          <p className="text-[10px] opacity-30 mt-3 text-center">
            Konten müssen zuerst verbunden sein · Verbindung über den Chat herstellen
          </p>
        </div>
      </div>

      {/* Video Info */}
      {(() => {
        const lang = LANGUAGES.find(l => l.id === p.language);
        const mood = MOODS.find(m => m.id === p.content_mood);
        const style = STYLES.find(s => s.id === p.subtitle_style);
        return (
          <div className="px-4 mt-4">
            <div className="gcard p-3">
              <h3 className="text-xs font-bold opacity-60 mb-2">📊 Video-Details</h3>
              <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="opacity-50">Format</div><div className="font-semibold">9:16</div>
                <div className="opacity-50">Sprache</div><div className="font-semibold">{lang?.flag} {lang?.label}</div>
                <div className="opacity-50">Thema</div><div className="font-semibold">{mood?.icon} {mood?.label}</div>
                <div className="opacity-50">Untertitel</div><div className="font-semibold">{style?.label}</div>
                <div className="opacity-50">Schrift</div><div className="font-semibold">{p.font}</div>
                <div className="opacity-50">Auto-Cut</div><div className="font-semibold">{isAutoCut(p) ? 'An' : 'Aus'}</div>
                <div className="opacity-50">Keywords</div><div className="font-semibold">{p.keywords ? p.keywords.split(',').length + ' aktiv' : 'Keine'}</div>
              </div>
            </div>
          </div>
        );
      })()}
    </div>
  );
}

// ═══════════════════════════════════════════════════════
// MAIN APP
// ═══════════════════════════════════════════════════════
function App() {
  const [view, setView] = useState<View>('dash');
  const [projects, setProjects] = useState<Project[]>([]);
  const [cur, setCur] = useState<Project | null>(null);
  const [loading, setLoading] = useState(true);
  const [dbError, setDbError] = useState(false);
  const [toast, setToast] = useState<{ msg: string; type: 'success' | 'error' | 'info' } | null>(null);

  const showToast = useCallback((msg: string, type: 'success' | 'error' | 'info') => {
    setToast({ msg, type });
  }, []);

  const dismissToast = useCallback(() => setToast(null), []);

  useEffect(() => {
    initDB().then(loadAll).then(setProjects).catch(() => setDbError(true)).finally(() => setLoading(false));
  }, []);

  // Poll for updates
  useEffect(() => {
    const iv = setInterval(async () => {
      try {
        const ps = await loadAll();
        setProjects(ps);
        if (cur) {
          const u = ps.find(x => x.id === cur.id);
          if (u) {
            const changed = JSON.stringify(u) !== JSON.stringify(cur);
            if (changed) setCur(u);
            if (u.status === 'done' && cur.status === 'processing') {
              setView('result');
              showToast('Video fertig! 🎉', 'success');
            }
            if (u.status === 'error' && cur.status === 'processing') {
              showToast('Fehler bei der Verarbeitung', 'error');
            }
          }
        }
      } catch {}
    }, 3000);
    return () => clearInterval(iv);
  }, [cur, showToast]);

  const [creatingNew, setCreatingNew] = useState(false);

  const handleNew = async () => {
    if (creatingNew) return;
    setCreatingNew(true);
    try {
      await B.sendMessageToAgent('Der User möchte ein neues ViralClip-Projekt erstellen. Bitte frage nach einem Video-Upload.');
      showToast('Neues Projekt wird vorbereitet...', 'info');
    } catch {
      showToast('Fehler beim Erstellen', 'error');
    } finally {
      setCreatingNew(false);
    }
  };

  const handleSelect = (p: Project) => { setCur(p); setView(p.status === 'done' ? 'result' : 'edit'); };

  const handleChange = async (k: string, v: string | number) => {
    if (!cur) return;
    const prev = cur;
    const u = { ...cur, [k]: v };
    setCur(u);
    try {
      await updateField(cur.id, k, v);
    } catch (err) {
      setCur(prev); // Revert optimistic update
      showToast('Speichern fehlgeschlagen', 'error');
    }
  };

  const [isProcessing, setIsProcessing] = useState(false);

  const handleProcess = async () => {
    if (!cur || isProcessing || !cur.input_path) return;
    setIsProcessing(true);
    try {
      await updateField(cur.id, 'status', 'processing');
      setCur({ ...cur, status: 'processing' });
      const config = {
        id: cur.id, input_path: cur.input_path, caption: cur.caption,
        font: cur.font, text_color: cur.text_color, highlight_color: cur.highlight_color,
        font_size: cur.font_size, subtitle_style: cur.subtitle_style,
        auto_cut: numVal(cur.auto_cut), language: cur.language, keywords: cur.keywords,
        content_mood: cur.content_mood, viral_sensitivity: cur.viral_sensitivity,
      };
      await B.writeFileToDisk(`/agent/home/viralclip-projects/${cur.id}/config.json`, JSON.stringify(config, null, 2));
      await B.sendMessageToAgent(`Verarbeite ViralClip-Projekt: ${cur.id}. Config: /agent/home/viralclip-projects/${cur.id}/config.json`);
      showToast('Verarbeitung gestartet! 🚀', 'info');
    } catch (err) {
      showToast('Fehler beim Starten der Verarbeitung', 'error');
      try { await updateField(cur.id, 'status', 'error'); } catch {}
      setCur({ ...cur, status: 'error' });
    } finally {
      setIsProcessing(false);
    }
  };

  const handleDelete = async () => {
    if (!cur) return;
    try {
      await B.sqlExec(`DELETE FROM vc_projects WHERE id='${esc(cur.id)}'`);
      setProjects(prev => prev.filter(x => x.id !== cur.id));
      setCur(null); setView('dash');
      showToast('Projekt gelöscht', 'success');
    } catch (err) {
      showToast('Löschen fehlgeschlagen', 'error');
    }
  };

  if (loading) return (
    <div data-theme="night" className="flex items-center justify-center h-screen bg-base-100">
      <div className="text-center">
        <span className="loading loading-spinner loading-lg text-primary" />
        <p className="mt-3 font-bold text-sm">ViralClip AI lädt...</p>
      </div>
    </div>
  );

  if (dbError) return (
    <div data-theme="night" className="flex items-center justify-center h-screen bg-base-100 p-6">
      <div className="text-center">
        <AlertTriangle size={48} className="text-error mx-auto mb-4" />
        <h2 className="text-lg font-bold mb-2">Datenbank-Fehler</h2>
        <p className="text-sm opacity-60 mb-4">Die Datenbank konnte nicht geladen werden.</p>
        <button className="btn btn-primary btn-sm rounded-xl" onClick={() => window.location.reload()}>
          <RefreshCw size={14} /> Neu laden
        </button>
      </div>
    </div>
  );

  return (
    <div data-theme="night" className="min-h-screen bg-base-100 text-base-content">
      {/* Toast Notification */}
      {toast && <Toast message={toast.msg} type={toast.type} onClose={dismissToast} />}

      {view === 'dash' && <Dash projects={projects} onSelect={handleSelect} onNew={handleNew} creatingNew={creatingNew} />}
      {view === 'edit' && cur && <Editor p={cur} onBack={() => { setView('dash'); setCur(null); }}
        onProcess={handleProcess} onChange={handleChange} onDelete={handleDelete} busy={isProcessing} />}
      {view === 'result' && cur && <Result p={cur} onBack={() => { setView('dash'); setCur(null); }}
        onReprocess={() => setView('edit')} onToast={showToast} />}
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
