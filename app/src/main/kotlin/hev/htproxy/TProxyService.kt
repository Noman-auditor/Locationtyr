package hev.htproxy

/**
 * JNI binding for hev-socks5-tunnel. The package and class name MUST stay hev.htproxy.TProxyService
 * because the native symbols are Java_hev_htproxy_TProxyService_*.
 * Needs libhev-socks5-tunnel.so in app/src/main/jniLibs/<abi>/ (see NATIVE.md).
 */
class TProxyService {
    companion object {
        @JvmStatic external fun TProxyStartService(configPath: String, fd: Int)
        @JvmStatic external fun TProxyStopService()
        @JvmStatic external fun TProxyGetStats(): LongArray?

        @JvmField var loaded: Boolean = try {
            System.loadLibrary("hev-socks5-tunnel"); true
        } catch (e: UnsatisfiedLinkError) { false }
    }
}
