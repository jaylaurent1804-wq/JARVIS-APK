import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import OpenAI from 'openai';

const app = express();
app.use(helmet());
app.use(express.json({ limit: '32kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 30, standardHeaders: true, legacyHeaders: false }));
const apiKey = process.env.OPENAI_API_KEY;
if (!apiKey) throw new Error('Set OPENAI_API_KEY in the server environment.');
const openai = new OpenAI({ apiKey });
const model = process.env.OPENAI_MODEL || 'gpt-6-astra';
app.get('/health', (_req, res) => res.json({ ok: true, service: 'JARVIS AI core' }));
app.post('/chat', async (req, res) => {
  try {
    const messages = req.body?.messages;
    if (!Array.isArray(messages) || messages.length === 0 || messages.length > 18) return res.status(400).json({ error: 'messages must be an array with 1–18 items' });
    const input = messages.map(m => ({ role: m.role === 'assistant' ? 'assistant' : 'user', content: String(m.content ?? '').slice(0, 6000) }));
    const response = await openai.responses.create({
      model,
      instructions: 'You are J.A.R.V.I.S., a precise, calm, helpful personal AI assistant. Be concise, capable, and transparent. Never claim to control a device or perform an action unless a connected tool actually did it.',
      input
    });
    res.json({ reply: response.output_text || 'I could not produce a text response.' });
  } catch (err) {
    console.error('JARVIS request failed:', err?.status || err?.message || 'unknown error');
    res.status(502).json({ error: 'The AI service could not complete the request.' });
  }
});
const port = Number(process.env.PORT || 3000);
app.listen(port, () => console.log(`JARVIS backend listening on ${port}`));
