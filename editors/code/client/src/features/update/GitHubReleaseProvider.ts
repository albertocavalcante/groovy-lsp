import { ReleaseProvider } from './ReleaseProvider';
import { VersionChecker, ReleaseInfo, GitHubReleaseResponse } from './VersionChecker';

/**
 * Production implementation of ReleaseProvider that fetches from GitHub API.
 */
export class GitHubReleaseProvider implements ReleaseProvider {
    private static readonly RELEASES_URL =
        'https://api.github.com/repos/GroovyLanguageServer/groovy-lsp/releases/latest';

    private readonly versionChecker: VersionChecker;

    constructor(versionChecker: VersionChecker = new VersionChecker()) {
        this.versionChecker = versionChecker;
    }

    async fetchLatestRelease(): Promise<ReleaseInfo | null> {
        try {
            const response = await fetch(GitHubReleaseProvider.RELEASES_URL, {
                headers: {
                    Accept: 'application/vnd.github.v3+json',
                    'User-Agent': 'vscode-groovy-extension'
                }
            });

            if (!response.ok) {
                console.warn(`GitHub API returned ${response.status}: ${response.statusText}`);
                return null;
            }

            const data = (await response.json()) as GitHubReleaseResponse;
            return this.versionChecker.buildReleaseInfo(data);
        } catch (error) {
            console.error('Failed to fetch latest release:', error);
            return null;
        }
    }
}
