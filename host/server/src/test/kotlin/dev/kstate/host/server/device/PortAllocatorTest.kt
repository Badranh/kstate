package dev.kstate.host.server.device

import kotlin.test.Test
import kotlin.test.assertEquals

class PortAllocatorTest {
    @Test
    fun handsOutPortsUpwardFromTheFirst() {
        val ports = PortAllocator(first = 47100, isFree = { true })

        assertEquals(listOf(47100, 47101, 47102), List(3) { ports.allocate() })
    }

    @Test
    fun aReleasedPortIsReused() {
        val ports = PortAllocator(first = 47100, isFree = { true })
        ports.allocate()
        val second = ports.allocate()

        ports.release(second)

        assertEquals(47101, ports.allocate())
    }

    @Test
    fun skipsPortsSomethingElseIsUsing() {
        val ports = PortAllocator(first = 47100, isFree = { it != 47100 })

        assertEquals(47101, ports.allocate())
    }
}
