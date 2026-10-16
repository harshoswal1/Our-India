// AI Lawyer proxy: the Groq key lives here as a Vercel environment variable, never in the APK.
//
// POST /api/lawyer  {"mode": "ask" | "draft", "query": "...", "history": [{"role": "user"|"assistant", "content": "..."}]}
//   200 {"answer": "..."}   4xx/5xx {"error": "..."}
//
// The system prompts are fixed here, so the endpoint is the app's legal assistant and not a
// free general-purpose LLM for anyone who finds the URL.

const GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
const MODEL = "openai/gpt-oss-120b";
const FALLBACK_MODEL = "openai/gpt-oss-20b";
const MAX_CHARS = 6000;
const MAX_HISTORY = 6;

const ASK_PROMPT = `You are "AI Lawyer" (न्याय साथी) inside the Our India app, a friendly legal guide for Indian citizens.
Explain Indian law in simple, everyday language so anyone can understand their rights and options.

How to answer:
1. Start with a two or three sentence plain explanation.
2. Then short sections, each starting with a bold heading on its own line, for example **What the law says**, **Your rights**, **What you can do now**, **Be careful about**.
3. Use numbered steps (1., 2., 3.) for actions. Be practical: where to go, what documents to carry, free legal aid (DLSA, NALSA 15100).
4. Do not use emoji, markdown headings (#), tables, or hyphen bullets. Bold is the only formatting.

Law:
- Cite the new criminal laws in force since 1 July 2024 alongside the old ones: Bharatiya Nyaya Sanhita (BNS) with the old IPC section, Bharatiya Nagarik Suraksha Sanhita (BNSS) with the old CrPC section, Bharatiya Sakshya Adhiniyam (BSA) with the old Evidence Act.
- Mention other statutes where relevant: Consumer Protection Act 2019, NI Act Section 138, Motor Vehicles Act, RTI Act 2005, IT Act, Domestic Violence Act 2005.
- If you are not sure of a section number, say so instead of guessing.
- If the question is not about law or legal procedure in India, say briefly that you only help with legal questions.
- End with one line: this is general legal information, and a local advocate should check anything before it is filed in court.`;

const DRAFT_PROMPT = `You are an experienced advocate in India who drafts legal notices, complaints and applications.
Write in formal, precise English following Indian drafting conventions. Do not use markdown symbols or emoji; use plain numbered paragraphs.
Only draft legal documents; decline anything else in one sentence.`;

// ponytail: per-instance memory, so the limit is rough (each warm instance counts separately and
// resets on cold start). Move to Upstash/Vercel KV if the key gets abused.
const WINDOW_MS = 10 * 60 * 1000;
const PER_WINDOW = 20;
const hits = new Map();

function limited(ip, now = Date.now()) {
  const recent = (hits.get(ip) || []).filter((t) => now - t < WINDOW_MS);
  recent.push(now);
  hits.set(ip, recent);
  if (hits.size > 5000) hits.clear();
  return recent.length > PER_WINDOW;
}

function bad(res, status, error) {
  res.status(status).json({ error });
}

async function complete(model, messages, key) {
  const r = await fetch(GROQ_URL, {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify({ model, messages, temperature: 0.3, max_tokens: 2048 }),
  });
  if (!r.ok) {
    const e = new Error(`Groq HTTP ${r.status}`);
    e.status = r.status;
    throw e;
  }
  const data = await r.json();
  const answer = data?.choices?.[0]?.message?.content?.trim();
  if (!answer) throw Object.assign(new Error("Empty answer"), { status: 502 });
  return answer;
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST") return bad(res, 405, "Use POST.");

  const key = process.env.GROQ_API_KEY;
  if (!key) return bad(res, 503, "AI answers aren't set up on the server yet.");

  // Optional shared secret. It ships in the APK too, so it only stops casual use of the URL.
  const appToken = process.env.LAWYER_APP_TOKEN;
  if (appToken && req.headers["x-app-token"] !== appToken) return bad(res, 401, "Not allowed.");

  const ip = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim() || "unknown";
  if (limited(ip)) return bad(res, 429, "Too many questions at once. Wait a few minutes and ask again.");

  let body = req.body;
  if (typeof body === "string") {
    try { body = JSON.parse(body); } catch { return bad(res, 400, "Send JSON."); }
  }
  const mode = body?.mode === "draft" ? "draft" : "ask";
  const query = typeof body?.query === "string" ? body.query.trim() : "";
  if (!query) return bad(res, 400, "Ask a question first.");
  if (query.length > MAX_CHARS) return bad(res, 400, "That's too long. Please shorten it.");

  const history = (Array.isArray(body?.history) ? body.history : [])
    .filter((m) => m && (m.role === "user" || m.role === "assistant") && typeof m.content === "string")
    .slice(-MAX_HISTORY)
    .map((m) => ({ role: m.role, content: m.content.slice(0, MAX_CHARS) }));

  const messages = [
    { role: "system", content: mode === "draft" ? DRAFT_PROMPT : ASK_PROMPT },
    ...(mode === "draft" ? [] : history),
    { role: "user", content: query },
  ];

  try {
    return res.status(200).json({ answer: await complete(MODEL, messages, key) });
  } catch (e) {
    if (e.status === 401 || e.status === 403) return bad(res, 503, "The AI service rejected the server's key.");
    if (e.status === 429) return bad(res, 429, "The AI service is busy. Please try again in a minute.");
    // The large model can be over capacity; the small one usually still answers.
    try {
      return res.status(200).json({ answer: await complete(FALLBACK_MODEL, messages, key) });
    } catch {
      return bad(res, 502, "The AI service is busy right now. Please try again in a minute.");
    }
  }
};

module.exports._test = { limited, hits, ASK_PROMPT, DRAFT_PROMPT };
