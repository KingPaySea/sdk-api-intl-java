import crypto from 'node:crypto'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// 仅收集显式制品，禁止整目录复制 target 或 src/test/resources，避免带出本地联调凭据。
const moduleDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const pom = await fs.readFile(path.join(moduleDir, 'pom.xml'), 'utf8')
const version = pom.match(/<artifactId>sdk-api-intl-java<\/artifactId>\s*<version>([^<]+)<\/version>/)?.[1]
if (!/^\d+\.\d+\.\d+$/.test(version || '')) throw new Error('A semantic SDK version is required.')
const name = `sdk-api-intl-java-${version}`
const output = path.join(moduleDir, 'target', 'release')
const files = [
  ['LICENSE', 'LICENSE'],
  ['pom.xml', 'pom.xml'],
  ['target/checksums/pom.xml.sha256', 'pom.xml.sha256'],
  ...['.jar', '-sources.jar', '-javadoc.jar', '-cyclonedx.json', '-cyclonedx.xml'].flatMap((suffix) => [
    [`target/${name}${suffix}`, `${name}${suffix}`],
    [`target/checksums/${name}${suffix}.sha256`, `${name}${suffix}.sha256`]
  ]),
  ['examples/Quickstart.java', 'Quickstart.java'],
  ['README.md', 'README.md'],
  ['CHANGELOG.md', 'CHANGELOG.md']
]
const contents = new Map()
for (const [source, filename] of files) {
  contents.set(filename, await fs.readFile(path.join(moduleDir, source)))
}
for (const [filename, content] of contents) {
  if (!filename.endsWith('.sha256')) continue
  const target = filename.slice(0, -7)
  const digest = crypto.createHash('sha256').update(contents.get(target)).digest('hex')
  if (content.toString('utf8').trim() !== `${digest} *${target}`
      && content.toString('utf8').trim() !== `${digest}  ${target}`) {
    throw new Error(`Missing or stale checksum for ${target}; run mvn verify first.`)
  }
}
await fs.mkdir(output, { recursive: true })
for (const [filename, content] of contents) await fs.writeFile(path.join(output, filename), content)
const hashes = Object.fromEntries([...contents].filter(([name]) => !name.endsWith('.sha256'))
  .map(([name, content]) => [name, crypto.createHash('sha256').update(content).digest('hex')]))
console.log(JSON.stringify({ version, directory: output, sha256: hashes }, null, 2))
