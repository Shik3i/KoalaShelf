// Generates small fictional sample audiobooks (sine tones + chapters + covers) with ffmpeg.
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { join } from 'node:path';

const root = join(import.meta.dirname, 'data', 'audiobooks');

const books = [
  { author: 'Mara Lindqvist', series: 'Küstenlichter', seq: 1, title: 'Der lange Weg', narrator: 'Ines Brandt', genre: 'Roman', color: '#2B4A7A', chapters: 6, cover: true },
  { author: 'Mara Lindqvist', series: 'Küstenlichter', seq: 2, title: 'Salzwasser', narrator: 'Ines Brandt', genre: 'Roman', color: '#8FB3C4', chapters: 4, cover: true },
  { author: 'Jonas Falk', title: 'Nachtzug', narrator: 'Paul Dahm', genre: 'Krimi', color: '#11183A', chapters: 5, cover: true, multiFile: true },
  { author: 'Tilda Weiss', title: 'Kleine Eulen', narrator: 'Hanna Voss', genre: 'Kinderbuch', color: '#C9563E', chapters: 3, cover: false },
  { author: 'Levin Aral', title: 'Glasfluss', narrator: 'Sarah Obermann', genre: 'Science-Fiction', color: '#F3E3CF', chapters: 4, cover: false },
  { author: 'Hanna Voss', title: 'Wintergrün', narrator: 'Jonas Falk', genre: 'Hörspiel', color: '#22533A', chapters: 3, cover: true },
  // Edge case: one plain file without chapter markers.
  { author: 'Paul Dahm', title: 'Ohne Kapitel', narrator: 'Paul Dahm', genre: 'Essay', color: '#4B3FB8', chapters: 1, cover: false, plain: true }
];

const CH = 45; // seconds per chapter
const ff = (...args) => execFileSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', ...args]);

for (const b of books) {
  const dir = b.series ? join(root, b.author, b.series, `${b.seq} - ${b.title}`) : join(root, b.author, b.title);
  if (existsSync(dir)) continue;
  mkdirSync(dir, { recursive: true });
  writeFileSync(join(dir, 'reader.txt'), b.narrator);
  writeFileSync(join(dir, 'desc.txt'), `${b.title} ist ein fiktives Beispiel-Hörbuch von ${b.author}, gelesen von ${b.narrator}. Es besteht aus Testtönen und dient nur der Entwicklung von KoalaShelf.`);
  if (b.cover) ff('-f', 'lavfi', '-i', `color=c=${b.color}:s=600x600`, '-frames:v', '1', join(dir, 'cover.jpg'));

  const tone = (i, secs) => ['-f', 'lavfi', '-i', `sine=frequency=${220 + i * 55}:duration=${secs}`];
  const tags = ['-metadata', `title=${b.title}`, '-metadata', `album=${b.title}`, '-metadata', `artist=${b.author}`, '-metadata', `genre=${b.genre}`];

  if (b.plain) {
    ff(...tone(0, 60), '-ac', '1', '-b:a', '32k', ...tags, join(dir, `${b.title}.mp3`));
  } else if (b.multiFile) {
    for (let i = 0; i < b.chapters; i++) {
      ff(...tone(i, CH), '-ac', '1', '-b:a', '32k', ...tags, '-metadata', `track=${i + 1}`, join(dir, `${String(i + 1).padStart(2, '0')} - Teil ${i + 1}.mp3`));
    }
  } else {
    const meta = [';FFMETADATA1'];
    for (let i = 0; i < b.chapters; i++) meta.push('[CHAPTER]', 'TIMEBASE=1/1', `START=${i * CH}`, `END=${(i + 1) * CH}`, `title=Kapitel ${i + 1}`);
    const metaFile = join(dir, 'chapters.txt');
    writeFileSync(metaFile, meta.join('\n'));
    const parts = Array.from({ length: b.chapters }, (_, i) => `sine=frequency=${220 + i * 55}:duration=${CH}`);
    ff('-i', metaFile, '-filter_complex', `${parts.map((p, i) => `${p}[a${i}]`).join(';')};${parts.map((_, i) => `[a${i}]`).join('')}concat=n=${parts.length}:v=0:a=1[out]`, '-map', '[out]', '-map_metadata', '0', '-map_chapters', '0', '-ac', '1', '-c:a', 'aac', '-b:a', '32k', ...tags, join(dir, `${b.title}.m4b`));
    rmSync(metaFile);
  }
  console.log('created', dir);
}
