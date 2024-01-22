package socket.adapter

import PlatformDispatcher
import io.ktor.network.selector.SelectorManager

// TODO change this to internal, add an interface in api module and add interface to this class, see if this fixes
//  the error in TcpSocket at private val selectorManager: SelectorManagerAdapter
class SelectorManagerAdapter {
    private val selectorManager by lazy { SelectorManager(PlatformDispatcher.io) }
    fun resolve() = selectorManager
}
