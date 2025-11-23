/**
 * Prepares the Groovy Language Server JAR
 * Priority order:
 * 1. Use existing server/groovy-lsp.jar if present (unless FORCE_DOWNLOAD=true)
 * 2. Copy from local groovy-lsp build if available and PREFER_LOCAL=true
 * 3. Download from GitHub releases
 */

const fs = require('fs');
const path = require('path');
const https = require('https');

const SERVER_DIR = path.join(__dirname, '..', 'server');
const CANONICAL_JAR_NAME = 'groovy-lsp.jar';
const JAR_PATH = path.join(SERVER_DIR, CANONICAL_JAR_NAME);

// GitHub release URLs
const GITHUB_RELEASE_API = 'https://api.github.com/repos/albertocavalcante/groovy-lsp/releases/latest';

/**
 * FIXME: Remove platform-specific logic once groovy-lsp publishes universal JAR
 * Currently we need to select platform-specific JARs because groovy-lsp publishes
 * separate JARs for each platform. This should be simplified to a single universal
 * JAR in the future for easier distribution and maintenance.
 * TODO: Create issue in groovy-lsp to publish universal JAR
 */

/**
 * Gets platform-specific JAR suffix for current OS
 * @returns {string} Platform suffix (e.g., 'linux-amd64', 'darwin-amd64', 'windows-amd64')
 */
function getPlatformJarSuffix() {
    const platformMap = {
        'linux': 'linux-amd64',
        'darwin': 'darwin-amd64',
        'win32': 'windows-amd64'
    };

    return platformMap[process.platform] || 'linux-amd64'; // Default to linux for unknown platforms
}

/**
 * Finds local Groovy LSP JAR file in common development locations
 */
function findLocalGroovyLspJar() {
    const searchPaths = [
        // 1. Environment variable override
        process.env.GROOVY_LSP_LOCAL_JAR,

        // 2. Sibling directory (common for development)
        path.join(__dirname, '..', '..', 'groovy-lsp', 'build', 'libs'),

        // 3. Common workspace patterns
        path.join(process.env.HOME || '', 'dev', 'workspace', 'groovy-lsp', 'build', 'libs'),
        path.join(process.env.HOME || '', 'workspace', 'groovy-lsp', 'build', 'libs'),
        path.join(process.env.HOME || '', 'projects', 'groovy-lsp', 'build', 'libs'),
    ].filter(Boolean); // Remove null/undefined paths

    for (const searchPath of searchPaths) {
        try {
            // If it's a direct file path (from env var), check if it exists
            if (searchPath === process.env.GROOVY_LSP_LOCAL_JAR) {
                if (fs.existsSync(searchPath) && searchPath.endsWith('.jar')) {
                    console.log(`Found local JAR via GROOVY_LSP_LOCAL_JAR: ${searchPath}`);
                    return searchPath;
                }
                continue;
            }

            // Otherwise, search for JAR files in the directory
            if (fs.existsSync(searchPath) && fs.statSync(searchPath).isDirectory()) {
                const jarFiles = fs.readdirSync(searchPath)
                    .filter(file => file.endsWith('.jar') && file.includes('groovy-lsp'))
                    .sort(); // Sort to get consistent results

                if (jarFiles.length > 0) {
                    const foundJar = path.join(searchPath, jarFiles[0]); // Use first match
                    console.log(`Found local JAR: ${foundJar}`);
                    return foundJar;
                }
            }
        } catch (error) {
            // Ignore errors for individual paths
            continue;
        }
    }

    console.log('No local Groovy LSP JAR found in common locations');
    return null;
}

/**
 * Fetches JSON data from a URL
 */
function fetchJson(url) {
    return new Promise((resolve, reject) => {
        const headers = {
            'User-Agent': 'vscode-groovy-extension'
        };

        // Add GitHub authentication if token is available (for CI rate limiting)
        if (process.env.GITHUB_TOKEN) {
            headers['Authorization'] = `token ${process.env.GITHUB_TOKEN}`;
        }

        const request = https.get(url, {
            headers: headers
        }, (response) => {
            let data = '';

            response.on('data', (chunk) => {
                data += chunk;
            });

            response.on('end', () => {
                try {
                    const json = JSON.parse(data);

                    // Check for HTTP error status codes
                    if (response.statusCode >= 400) {
                        reject(new Error(`HTTP ${response.statusCode}: ${json.message || 'Request failed'}`));
                        return;
                    }

                    resolve(json);
                } catch (error) {
                    reject(new Error(`Failed to parse JSON: ${error.message}`));
                }
            });
        });

        request.on('error', (error) => {
            reject(error);
        });

        request.setTimeout(30000, () => {
            request.destroy();
            reject(new Error('Request timeout'));
        });
    });
}

/**
 * Downloads a file from URL to local path
 */
function downloadFile(url, filePath) {
    return new Promise((resolve, reject) => {
        function handleRequest(requestUrl) {
            const request = https.get(requestUrl, (response) => {
                // Handle redirects
                if (response.statusCode >= 300 && response.statusCode < 400 && response.headers.location) {
                    console.log(`Following redirect...`);
                    return handleRequest(response.headers.location);
                }

                if (response.statusCode !== 200) {
                    reject(new Error(`HTTP ${response.statusCode}: ${response.statusMessage}`));
                    return;
                }

                // Create file stream only when we have a successful response
                const file = fs.createWriteStream(filePath);

                file.on('error', (error) => {
                    fs.unlink(filePath, () => {}); // Clean up on error
                    reject(error);
                });

                file.on('finish', () => {
                    file.close();
                    resolve();
                });

                response.pipe(file);
            });

            request.on('error', (error) => {
                fs.unlink(filePath, () => {}); // Clean up on error
                reject(error);
            });

            request.setTimeout(60000, () => {
                request.destroy();
                reject(new Error('Download timeout'));
            });
        }

        handleRequest(url);
    });
}

/**
 * Gets the latest or most recent release info, handling prereleases
 * @returns {Promise<Object>} Release information object
 */
async function getLatestReleaseInfo() {
    try {
        console.log('Fetching latest release info from GitHub...');
        // Try latest release first (non-prereleases only)
        return await fetchJson(GITHUB_RELEASE_API);
    } catch (error) {
        if (error.message.includes('404') || error.message.includes('Not Found')) {
            console.log('No "latest" release found, checking all releases...');
            // Fallback to first available release (including prereleases)
            const releasesUrl = GITHUB_RELEASE_API.replace('/latest', '');
            const releases = await fetchJson(releasesUrl);

            if (releases && releases.length > 0) {
                console.log(`Using most recent release: ${releases[0].tag_name}`);
                return releases[0];
            }
        }
        throw error;
    }
}

/**
 * Selects the appropriate JAR asset for the current platform
 * @param {Array} assets Array of release assets
 * @returns {Object|null} Selected JAR asset or null if not found
 */
function selectPlatformJar(assets) {
    if (!assets || assets.length === 0) return null;

    const platformSuffix = getPlatformJarSuffix();

    // TODO: Simplify to single JAR download once groovy-lsp publishes universal JAR
    // First, try to find platform-specific JAR
    let jarAsset = assets.find(a =>
        a.name.endsWith('.jar') &&
        a.name.includes(platformSuffix)
    );

    if (!jarAsset) {
        console.log(`Platform-specific JAR (${platformSuffix}) not found, trying any JAR...`);
        // Fallback to any JAR file
        jarAsset = assets.find(a => a.name.endsWith('.jar'));
    }

    return jarAsset;
}

/**
 * Downloads the latest release JAR from GitHub
 */
async function downloadLatestRelease() {
    try {
        const releaseInfo = await getLatestReleaseInfo();

        if (!releaseInfo.assets || releaseInfo.assets.length === 0) {
            throw new Error('No assets found in release');
        }

        const jarAsset = selectPlatformJar(releaseInfo.assets);

        if (!jarAsset) {
            throw new Error('No JAR file found in release');
        }

        console.log(`Downloading ${jarAsset.name} from release ${releaseInfo.tag_name}...`);
        await downloadFile(jarAsset.browser_download_url, JAR_PATH);
        console.log(`✓ Downloaded and saved as ${CANONICAL_JAR_NAME}`);

    } catch (error) {
        throw new Error(`Failed to download from GitHub releases: ${error.message}`);
    }
}

/**
 * Main function to prepare the server JAR
 */
async function prepareServer() {
    try {
        // Ensure server directory exists
        if (!fs.existsSync(SERVER_DIR)) {
            fs.mkdirSync(SERVER_DIR, { recursive: true });
            console.log('Created server directory');
        }

        const forceDownload = process.env.FORCE_DOWNLOAD === 'true';
        const preferLocal = process.env.PREFER_LOCAL === 'true';

        // Try local build first if preferred
        if (preferLocal) {
            const localJarPath = findLocalGroovyLspJar();

            if (localJarPath) {
                let shouldCopy = true;

                if (!forceDownload && fs.existsSync(JAR_PATH)) {
                    // Compare timestamps to see if local build is newer
                    const localStat = fs.statSync(localJarPath);
                    const existingStat = fs.statSync(JAR_PATH);

                    if (localStat.mtime <= existingStat.mtime) {
                        console.log(`✓ Using existing ${CANONICAL_JAR_NAME} (up to date)`);
                        shouldCopy = false;
                    }
                }

                if (shouldCopy) {
                    console.log(`Copying from local build: ${localJarPath}`);
                    fs.copyFileSync(localJarPath, JAR_PATH);
                    console.log(`✓ Copied to ${CANONICAL_JAR_NAME}`);
                }
                return;
            } else {
                console.log('PREFER_LOCAL=true but no local JAR found, falling back to download...');
            }
        }

        // Check if JAR already exists (unless force download)
        if (!forceDownload && fs.existsSync(JAR_PATH)) {
            console.log(`✓ Using existing ${CANONICAL_JAR_NAME}`);
            return;
        }

        // Download from GitHub releases
        console.log('Downloading from GitHub releases...');
        await downloadLatestRelease();

    } catch (error) {
        console.error('❌ Error preparing Groovy Language Server:');
        console.error(error.message);

        // Provide helpful error messages
        if (error.message.includes('ENOTFOUND') || error.message.includes('timeout')) {
            console.error('');
            console.error('Network error. Please check your internet connection or try again later.');
        } else if (error.message.includes('No JAR file found')) {
            console.error('');
            console.error('No JAR files found in the latest release.');
            console.error('Please check: https://github.com/albertocavalcante/groovy-lsp/releases');
        }

        console.error('');
        console.error('You can also manually copy a JAR file to:');
        console.error(`  ${JAR_PATH}`);

        // In CI environments or if specifically requested, don't fail the build
        // This allows npm install to succeed even if the server JAR cannot be downloaded
        // GitHub Actions sets both CI=true and GITHUB_ACTIONS=true
        if (process.env.CI || process.env.GITHUB_ACTIONS || process.env.IGNORE_DOWNLOAD_FAILURE === 'true') {
            console.warn('\n⚠️ WARNING: Server JAR download failed, but ignoring failure (CI/GITHUB_ACTIONS/IGNORE_DOWNLOAD_FAILURE).');
            console.warn('The extension will NOT work without the server JAR unless a custom path is configured.');
            process.exit(0);
        }

        process.exit(1);
    }
}

// Run the script
if (require.main === module) {
    console.log('Preparing Groovy Language Server...');
    prepareServer().then(() => {
        console.log('✅ Server preparation complete!');
    });
}