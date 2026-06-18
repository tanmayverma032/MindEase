import cv2
import mediapipe as mp
import numpy as np
from fastapi import FastAPI, UploadFile, File
import tempfile
import os

app = FastAPI()

# Use mediapipe 0.10.8 compatible import
mp_face_mesh = mp.solutions.face_mesh

LEFT_EYE = [33, 160, 158, 133, 153, 144]
RIGHT_EYE = [362, 385, 387, 263, 373, 380]


def calculate_EAR(eye_points, landmarks, w, h):
    points = []
    for point in eye_points:
        lm = landmarks.landmark[point]
        points.append((int(lm.x * w), int(lm.y * h)))

    # vertical distances
    A = np.linalg.norm(np.array(points[1]) - np.array(points[5]))
    B = np.linalg.norm(np.array(points[2]) - np.array(points[4]))
    # horizontal distance
    C = np.linalg.norm(np.array(points[0]) - np.array(points[3]))

    ear = (A + B) / (2.0 * C)
    return ear


@app.post("/blink-count")
async def blink_count(video: UploadFile = File(...)):
    blink_counter = 0
    ear_threshold = 0.21
    consecutive_frames = 2
    frame_counter = 0

    # Save uploaded file temporarily
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp4") as temp_video:
        temp_video.write(await video.read())
        temp_path = temp_video.name

    cap = cv2.VideoCapture(temp_path)

    with mp_face_mesh.FaceMesh(
        static_image_mode=False,
        max_num_faces=1,
        refine_landmarks=True,
        min_detection_confidence=0.5,
        min_tracking_confidence=0.5
    ) as face_mesh:

        while cap.isOpened():
            success, frame = cap.read()
            if not success:
                break

            h, w, _ = frame.shape
            rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            results = face_mesh.process(rgb_frame)

            if results.multi_face_landmarks:
                landmarks = results.multi_face_landmarks[0]

                left_ear = calculate_EAR(LEFT_EYE, landmarks, w, h)
                right_ear = calculate_EAR(RIGHT_EYE, landmarks, w, h)

                ear = (left_ear + right_ear) / 2.0

                if ear < ear_threshold:
                    frame_counter += 1
                else:
                    if frame_counter >= consecutive_frames:
                        blink_counter += 1
                    frame_counter = 0

    cap.release()
    os.remove(temp_path)

    return {"blink_count": blink_counter}
