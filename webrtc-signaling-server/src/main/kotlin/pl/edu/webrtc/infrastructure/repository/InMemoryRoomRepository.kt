package pl.edu.webrtc.infrastructure.repository

import jakarta.enterprise.context.ApplicationScoped
import pl.edu.webrtc.domain.model.Room
import pl.edu.webrtc.domain.model.RoomCode
import pl.edu.webrtc.domain.repository.RoomRepository
import java.util.concurrent.ConcurrentHashMap

@ApplicationScoped
class InMemoryRoomRepository : RoomRepository {

    private val rooms = ConcurrentHashMap<RoomCode, Room>()

    override fun save(room: Room) {
        rooms[room.code] = room
    }

    override fun findByCode(code: RoomCode): Room? {
        return rooms[code]
    }

    override fun deleteByCode(code: RoomCode) {
        rooms.remove(code)
    }

    override fun existsByCode(code: RoomCode): Boolean {
        return rooms.containsKey(code)
    }
}