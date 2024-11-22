import {
    ChimeSDKMeetingsClient,
    CreateMeetingCommand,
} from '@aws-sdk/client-chime-sdk-meetings'; // ES Modules import

const client = new ChimeSDKMeetingsClient({
    region: process.env.region,
    credentials: {
        accessKeyId: process.env.access_key,
        secretAccessKey: process.env.secret_key,
    },
});

export default async function handler(req, res) {
    try {
        const createMeeting = async (token = '23232', externalId = 'auth-user-id') => {
            const params = {
                ClientRequestToken: token,
                ExternalMeetingId: externalId,
                MediaRegion: '', // depending on your region
            };

            const request = new CreateMeetingCommand(params);

            return await client.send(request);
        };

        const meetingResponse = await createMeeting();

        return res.json(res, 200, 'Meeting created successfully', {
            meeting: meetingResponse.Meeting,
            attendee: meetingResponse.Attendee,
        });
    } catch (error) {
        return errorResponse(res, 500, error.message);
    }
}
