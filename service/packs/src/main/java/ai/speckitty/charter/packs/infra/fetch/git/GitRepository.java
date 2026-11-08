package ai.speckitty.charter.packs.infra.fetch.git;

/**
 * A repository the request named, rebuilt from the parts of the parsed URL and never kept as sent (squad 3): the
 * lower-case host and the path, over https on port 443. The pin of the address and the argument git is given are both
 * made from this value, so another spelling of the host cannot slip past the pin.
 *
 * @param host the lower-case host name, on the allowlist
 * @param url the canonical URL, {@code https://<host><path>}, which git is given after {@code --}
 */
public record GitRepository(String host, String url) {}
