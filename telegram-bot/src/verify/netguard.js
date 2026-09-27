import { BlockList, isIP } from "node:net";
import { lookup as dnsLookup } from "node:dns";

/**
 * Address ranges the playlist verifier must never connect to. A contributor
 * controls the URL, so without this the server would fetch its own loopback,
 * the VPS's private network, or the cloud metadata endpoint on their behalf.
 */
const blocked = new BlockList();

for (const [net, prefix] of [
  ["0.0.0.0", 8],
  ["10.0.0.0", 8],
  ["100.64.0.0", 10], // carrier-grade NAT
  ["127.0.0.0", 8],
  ["169.254.0.0", 16], // link-local, incl. 169.254.169.254 metadata
  ["172.16.0.0", 12],
  ["192.0.0.0", 24],
  ["192.0.2.0", 24],
  ["192.88.99.0", 24],
  ["192.168.0.0", 16],
  ["198.18.0.0", 15],
  ["198.51.100.0", 24],
  ["203.0.113.0", 24],
  ["224.0.0.0", 4], // multicast
  ["240.0.0.0", 4], // reserved + broadcast
]) {
  blocked.addSubnet(net, prefix, "ipv4");
}

for (const [net, prefix] of [
  ["::", 96], // unspecified, loopback and deprecated IPv4-compatible (::a.b.c.d)
  ["::ffff:0:0:0", 96], // IPv4-translated
  ["64:ff9b::", 96], // NAT64 can reach any IPv4, private ones included
  ["100::", 64],
  ["2001::", 32], // Teredo
  ["2001:db8::", 32],
  ["2002::", 16], // 6to4 embeds an IPv4 address
  ["fc00::", 7], // unique local
  ["fe80::", 10], // link-local
  ["ff00::", 8], // multicast
]) {
  blocked.addSubnet(net, prefix, "ipv6");
}

/** True only for globally routable unicast addresses. */
export function isPublicAddress(address) {
  const family = isIP(address);
  if (family === 0) return false;
  if (family === 6) {
    const mapped = address.toLowerCase().match(/^::ffff:(\d+\.\d+\.\d+\.\d+)$/);
    if (mapped) return isPublicAddress(mapped[1]);
    // ::ffff:7f00:1 is the hex spelling of the same mapped range.
    if (/^::ffff:[0-9a-f]{1,4}:[0-9a-f]{1,4}$/i.test(address)) return false;
    return !blocked.check(address, "ipv6");
  }
  return !blocked.check(address, "ipv4");
}

/**
 * A drop-in for dns.lookup that refuses non-public answers. Passed as the
 * `lookup` option of http.request, so the address checked is the address
 * connected to — a hostname cannot resolve to a public IP for the check and a
 * private one for the connection (DNS rebinding).
 */
export function createSafeLookup(resolve = dnsLookup) {
  return function safeLookup(hostname, options, callback) {
    if (typeof options === "function") {
      callback = options;
      options = {};
    }
    resolve(hostname, { ...options, all: true }, (error, addresses) => {
      if (error) return callback(error);
      const list = Array.isArray(addresses) ? addresses : [{ address: addresses, family: options.family }];
      const refused = list.find((entry) => !isPublicAddress(entry.address));
      if (refused || list.length === 0) {
        const err = new Error(`Refusing to connect to non-public address for ${hostname}`);
        err.code = "EPRIVATEADDRESS";
        return callback(err);
      }
      if (options.all) return callback(null, list);
      return callback(null, list[0].address, list[0].family);
    });
  };
}
