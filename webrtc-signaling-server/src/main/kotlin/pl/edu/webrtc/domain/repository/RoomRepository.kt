package pl.edu.webrtc.domain.repository

import pl.edu.webrtc.domain.model.Room
import pl.edu.webrtc.domain.model.RoomCode

interface RoomRepository {
    fun save(room: Room)
    fun findByCode(code: RoomCode): Room?
    fun deleteByCode(code: RoomCode)
    fun existsByCode(code: RoomCode): Boolean

}