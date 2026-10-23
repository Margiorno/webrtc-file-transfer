import { useState, useEffect } from 'react';
import { roomApiService } from '../services/RoomApiService';
import { signalingService } from '../services/SignalingService';
import type {
    ClientId,
    ClientSecret,
    DeviceMetadata,
    JoinDecision,
    RoomCode,
    RoomState,
    SecurityMode,
} from '../types/Models';

export function useRoomSession() {
    const [isLoading, setIsLoading] = useState<boolean>(false);
    const [role, setRole] = useState<'HOST' | 'GUEST' | null>(null);
    const [roomCode, setRoomCode] = useState<RoomCode | null>(null);
    const [roomState, setRoomState] = useState<RoomState | null>(null);
    const [clientId, setClientId] = useState<ClientId | null>(null);
    const [clientSecret, setClientSecret] = useState<ClientSecret | null>(null);

    const [guestMetadata, setGuestMetadata] = useState<DeviceMetadata | null>(null);
    const [hostTwoFactorCode, setHostTwoFactorCode] = useState<string | null>(null);
    const [guestTwoFactorOptions, setGuestTwoFactorOptions] = useState<string[]>([]);

    useEffect(() => {
        const unsubscribe = signalingService.onMessage((msg) => {
            if (msg.state) {
                setRoomState(msg.state);
            }

            if (msg.type === 'GUEST_JOIN_REQUESTED') {
                setRoomState('PENDING_HOST_APPROVAL');
                setGuestMetadata((msg.payload as DeviceMetadata) ?? null);
            }

            if (msg.type === 'TWO_FACTOR_CHALLENGE') {
                setRoomState('PENDING_2FA');
                const payload = msg.payload as { options?: string[] } | string[] | undefined;
                const options = Array.isArray(payload) ? payload : payload?.options ?? [];
                setGuestTwoFactorOptions(options);
            }

            if (msg.type === 'ROOM_ACTIVE') {
                setRoomState('ACTIVE');
            }

            if (msg.type === 'JOIN_REJECTED' || msg.type === 'PEER_DISCONNECTED') {
                setRoomState('CLOSED');
            }
        });

        return () => unsubscribe();
    }, []);

    const createRoom = async (securityMode: SecurityMode) => {
        try {
            setIsLoading(true);
            const response = await roomApiService.createRoom(securityMode);
            await signalingService.connect(response.roomCode, response.hostId, response.hostSecret);

            setRole('HOST');
            setRoomCode(response.roomCode);
            setRoomState(response.state);
            setClientId(response.hostId);
            setClientSecret(response.hostSecret);
        } catch (error) {
            console.error('Failed to create room:', error);
            alert('Error creating room');
        } finally {
            setIsLoading(false);
        }
    };

    const joinRoom = async (codeToJoin: RoomCode) => {
        try {
            setIsLoading(true);
            const response = await roomApiService.joinRoom(codeToJoin);
            await signalingService.connect(codeToJoin, response.guestId, response.guestSecret);

            setRole('GUEST');
            setRoomCode(codeToJoin);
            setRoomState(response.state);
            setClientId(response.guestId);
            setClientSecret(response.guestSecret);
        } catch (error) {
            console.error('Failed to join room:', error);
            alert('Error joining room. Check if the room code is valid.');
        } finally {
            setIsLoading(false);
        }
    };

    const resolveJoin = async (decision: JoinDecision) => {
        if (!roomCode || !clientId || !clientSecret) return;

        try {
            setIsLoading(true);
            const response = await roomApiService.resolveJoin(roomCode, clientId, clientSecret, decision);
            setRoomState(response.state);

            const code = response.twoFactorCorrectCode ?? response.correctCode ?? null;
            if (code) {
                setHostTwoFactorCode(code);
            }
        } catch (error) {
            console.error('Failed to resolve join:', error);
            alert('Failed to resolve guest request');
        } finally {
            setIsLoading(false);
        }
    };

    const submit2FA = async (selectedCode: string) => {
        if (!roomCode || !clientId || !clientSecret) return;

        try {
            setIsLoading(true);
            const response = await roomApiService.submit2FA(roomCode, clientId, clientSecret, selectedCode);
            setRoomState(response.state);
        } catch (error) {
            console.error('Failed 2FA verification:', error);
            alert('Invalid 2FA code or room closed.');
            setRoomState('CLOSED');
        } finally {
            setIsLoading(false);
        }
    };

    const leaveRoom = () => {
        signalingService.disconnect();
        sessionStorage.clear();
        setRole(null);
        setRoomCode(null);
        setRoomState(null);
        setClientId(null);
        setClientSecret(null);
        setGuestMetadata(null);
        setHostTwoFactorCode(null);
        setGuestTwoFactorOptions([]);
    };

    return {
        isLoading,
        role,
        roomCode,
        roomState,
        guestMetadata,
        hostTwoFactorCode,
        guestTwoFactorOptions,
        createRoom,
        joinRoom,
        resolveJoin,
        submit2FA,
        leaveRoom,
    };
}