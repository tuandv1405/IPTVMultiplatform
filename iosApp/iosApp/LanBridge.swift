import CryptoKit
import Foundation
import Network
import ComposeApp

/// The iPhone side of cast / send to TV (`IosLanBridge` in `IosLan.kt`). Registered in
/// `iOSApp.init()`: `IosLan.shared.bridge = LanBridge()`.
///
/// Needs `NSBonjourServices` (`_tsiptv._tcp`) and `NSLocalNetworkUsageDescription` in Info.plist:
/// without them Bonjour finds nothing and TCP to a LAN address never connects.
/// Never log a host or a request line (they carry pairing data and stream links).
final class LanBridge: NSObject, IosLanBridge {

    func startBrowsing(listener: IosLanBrowseListener) -> IosLanTask {
        let browse = LanBrowse(listener: listener)
        DispatchQueue.main.async { browse.start() }
        return browse
    }

    func localNetworks() -> [LanNetwork] {
        var result: [LanNetwork] = []
        var list: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&list) == 0, let first = list else { return [] }
        defer { freeifaddrs(list) }
        for pointer in sequence(first: first, next: { $0.pointee.ifa_next }) {
            let entry = pointer.pointee
            let flags = Int32(entry.ifa_flags)
            guard (flags & IFF_UP) != 0, (flags & IFF_RUNNING) != 0, (flags & IFF_LOOPBACK) == 0,
                  (flags & IFF_POINTOPOINT) == 0 else { continue }
            // en0 is Wi-Fi, other "en" are wired adapters; pdp_ip (mobile), utun (VPN) and the rest are not the TV's LAN.
            guard String(cString: entry.ifa_name).hasPrefix("en") else { continue }
            guard let addr = entry.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET),
                  let mask = entry.ifa_netmask else { continue }
            let ip = addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
            let netmask = mask.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr }
            let host = UInt32(bigEndian: ip)
            guard LanBridge.isPrivate(host) else { continue }
            result.append(LanNetwork(address: LanBridge.format(host), prefixLength: Int32(netmask.nonzeroBitCount)))
        }
        return result
    }

    func exchange(host: String, port: Int32, line: String, maxAnswerBytes: Int32, connectTimeoutMs: Int32,
                  readTimeoutMs: Int32, completion: IosLanCompletion) -> IosLanTask {
        let call = LanExchange(completion: completion, maxAnswerBytes: Int(maxAnswerBytes))
        call.start(host: host, port: port, line: line, connectTimeoutMs: Int(connectTimeoutMs), readTimeoutMs: Int(readTimeoutMs))
        return call
    }

    func createKeyAgreement() -> IosLanKeyAgreement? {
        LanKeyAgreement()
    }

    /// 10/8, 172.16/12, 192.168/16 and link-local 169.254/16.
    private static func isPrivate(_ ip: UInt32) -> Bool {
        let a = ip >> 24, b = (ip >> 16) & 0xFF
        return a == 10 || (a == 172 && (16...31).contains(b)) || (a == 192 && b == 168) || (a == 169 && b == 254)
    }

    fileprivate static func format(_ ip: UInt32) -> String {
        [24, 16, 8, 0].map { String((ip >> UInt32($0)) & 0xFF) }.joined(separator: ".")
    }
}

/// Ephemeral P-256 ECDH. CryptoKit's DER is the X.509 SubjectPublicKeyInfo the TV (`java.security`)
/// sends and expects; the shared secret is the raw x coordinate, like JCA's "ECDH".
private final class LanKeyAgreement: NSObject, IosLanKeyAgreement {
    private let privateKey = P256.KeyAgreement.PrivateKey()

    var publicKeyDer: Data { privateKey.publicKey.derRepresentation }

    func agree(peerPublicKeyDer: Data) -> Data? {
        guard let peer = try? P256.KeyAgreement.PublicKey(derRepresentation: peerPublicKeyDer),
              let secret = try? privateKey.sharedSecretFromKeyAgreement(with: peer) else { return nil }
        return secret.withUnsafeBytes { Data($0) }
    }
}

/// One Bonjour browse of `_tsiptv._tcp`, resolving each service for its IPv4 address and TXT `id`/`v`.
/// Main thread only (NetService runs on the main run loop).
private final class LanBrowse: NSObject, IosLanTask, NetServiceBrowserDelegate, NetServiceDelegate {
    private let listener: IosLanBrowseListener
    private let browser = NetServiceBrowser()
    private var resolving: [NetService] = []
    private var found: [String: LanDevice] = [:]
    private var attempts: [String: Int] = [:]
    private var stopped = false

    init(listener: IosLanBrowseListener) {
        self.listener = listener
    }

    func start() {
        guard !stopped else { return }
        browser.delegate = self
        browser.searchForServices(ofType: "\(LanProtocol.shared.SERVICE_TYPE).", inDomain: "local.")
    }

    func cancel() {
        DispatchQueue.main.async {
            self.stopped = true
            self.browser.stop()
            self.resolving.forEach { $0.stop() }
            self.resolving.removeAll()
        }
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        resolve(service)
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didRemove service: NetService, moreComing: Bool) {
        resolving.removeAll { $0 == service }
        if found.removeValue(forKey: service.name) != nil { publish() }
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        NSLog("TsLan: Bonjour browse failed: \(errorDict)")
        publish()
    }

    func netServiceDidResolveAddress(_ sender: NetService) {
        guard let host = Self.ipv4(of: sender) ?? Self.anyAddress(of: sender) else { return }
        resolving.removeAll { $0 == sender }
        sender.stop()
        attempts[sender.name] = nil
        let txt = sender.txtRecordData().map { NetService.dictionary(fromTXTRecord: $0) } ?? [:]
        let id = txt[LanProtocol.shared.TXT_ID].flatMap { String(data: $0, encoding: .utf8) } ?? ""
        let version = txt[LanProtocol.shared.TXT_VERSION].flatMap { String(data: $0, encoding: .utf8) }.flatMap { Int32($0) } ?? 1
        found[sender.name] = LanDevice(id: id, name: sender.name, host: host, port: Int32(sender.port), version: version)
        publish()
    }

    func netService(_ sender: NetService, didNotResolve errorDict: [String: NSNumber]) {
        resolving.removeAll { $0 == sender }
        let tries = (attempts[sender.name] ?? 0) + 1
        attempts[sender.name] = tries
        NSLog("TsLan: Bonjour resolve failed (try \(tries))")
        // A service is reported once: retry a failed resolve a few times, or that TV stays hidden.
        guard tries < 3 else { return }
        DispatchQueue.main.asyncAfter(deadline: .now() + .seconds(tries)) { [weak self] in
            self?.resolve(sender)
        }
    }

    private func resolve(_ service: NetService) {
        guard !stopped else { return }
        resolving.append(service)
        service.delegate = self
        service.resolve(withTimeout: 5)
    }

    private func publish() {
        guard !stopped else { return }
        listener.onDevices(devices: Array(found.values))
    }

    private static func ipv4(of service: NetService) -> String? {
        for data in service.addresses ?? [] {
            guard data.count >= MemoryLayout<sockaddr_in>.size else { continue }
            var sin = sockaddr_in()
            _ = withUnsafeMutableBytes(of: &sin) { data.copyBytes(to: $0) }
            if sin.sin_family == UInt8(AF_INET) { return LanBridge.format(UInt32(bigEndian: sin.sin_addr.s_addr)) }
        }
        return nil
    }

    private static func anyAddress(of service: NetService) -> String? {
        for data in service.addresses ?? [] {
            var name = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let ok = data.withUnsafeBytes { raw -> Bool in
                guard let base = raw.baseAddress?.assumingMemoryBound(to: sockaddr.self) else { return false }
                return getnameinfo(base, socklen_t(data.count), &name, socklen_t(name.count), nil, 0, NI_NUMERICHOST) == 0
            }
            if ok { return String(cString: name) }
        }
        return nil
    }
}

/// One request per TCP connection (protocol v1): send the line, read one `\n`-terminated answer of
/// at most `maxAnswerBytes`. The connect and read timeouts are our own timers (NWConnection only
/// has whole seconds, and the address scan needs 700 ms).
private final class LanExchange: NSObject, IosLanTask {
    private static let queue = DispatchQueue(label: "tsiptv.lan.exchange")

    private let completion: IosLanCompletion
    private let maxAnswerBytes: Int
    private var connection: NWConnection?
    private var buffer = Data()
    private var done = false
    private var timer: DispatchWorkItem?

    init(completion: IosLanCompletion, maxAnswerBytes: Int) {
        self.completion = completion
        self.maxAnswerBytes = maxAnswerBytes
    }

    func start(host: String, port: Int32, line: String, connectTimeoutMs: Int, readTimeoutMs: Int) {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(clamping: port)), port > 0 else {
            Self.queue.async { self.finish(nil, "Bad port") }
            return
        }
        let tcp = NWProtocolTCP.Options()
        tcp.noDelay = true
        let parameters = NWParameters(tls: nil, tcp: tcp)
        // Never over mobile data: the TV is on the Wi-Fi (or wired) LAN.
        parameters.prohibitedInterfaceTypes = [.cellular]
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: parameters)
        Self.queue.async {
            self.connection = connection
            self.arm(ms: connectTimeoutMs, reason: "Connect timeout")
            // Strong on purpose: `finish` clears the handler, which ends the cycle.
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    self.arm(ms: readTimeoutMs, reason: "Read timeout")
                    connection.send(content: Data((line + "\n").utf8), completion: .contentProcessed { error in
                        if let error { self.finish(nil, "Send: \(error)") }
                    })
                    self.receive()
                case .failed(let error):
                    self.finish(nil, "Failed: \(error)")
                case .cancelled:
                    self.finish(nil, "Cancelled")
                default:
                    break
                }
            }
            connection.start(queue: Self.queue)
        }
    }

    func cancel() {
        Self.queue.async { self.finish(nil, "Cancelled") }
    }

    private func arm(ms: Int, reason: String) {
        timer?.cancel()
        let item = DispatchWorkItem { [weak self] in self?.finish(nil, reason) }
        timer = item
        Self.queue.asyncAfter(deadline: .now() + .milliseconds(ms), execute: item)
    }

    private func receive() {
        connection?.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self, !self.done else { return }
            if let data, !data.isEmpty {
                if let newline = data.firstIndex(of: UInt8(ascii: "\n")) {
                    self.buffer.append(data[data.startIndex..<newline])
                    self.answer()
                    return
                }
                self.buffer.append(data)
                if self.buffer.count > self.maxAnswerBytes {
                    self.finish(nil, "Answer too long")
                    return
                }
            }
            if isComplete || error != nil {
                // The TV closed the connection: what came so far is the answer (like the JVM transport).
                if self.buffer.isEmpty { self.finish(nil, "No answer") } else { self.answer() }
                return
            }
            self.receive()
        }
    }

    private func answer() {
        if buffer.count > maxAnswerBytes {
            finish(nil, "Answer too long")
        } else if let text = String(data: buffer, encoding: .utf8) {
            finish(text, nil)
        } else {
            finish(nil, "Not UTF-8")
        }
    }

    /// Exactly one completion; always on `queue`.
    private func finish(_ answer: String?, _ error: String?) {
        guard !done else { return }
        done = true
        timer?.cancel()
        timer = nil
        connection?.stateUpdateHandler = nil
        connection?.cancel()
        connection = nil
        completion.onResult(answer: answer, error: error)
    }
}
