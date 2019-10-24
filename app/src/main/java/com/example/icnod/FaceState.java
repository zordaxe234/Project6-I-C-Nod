package com.example.icnod;

import android.content.Context;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

class FaceState {
    private List<FaceDirection> faceDirection = new ArrayList<>();

    public void addFaceDirection(FaceDirection faceDirection, Context context) {
        if (this.getDirection() != faceDirection){ //we dont want to add multiples of the same movement
            this.faceDirection.add(faceDirection);
            int action = this.detectAction();
            switch (action){
                case 0:
                    break;
                case 1:
                    Toast.makeText(context, "Nod Detected", Toast.LENGTH_SHORT).show();
                    break;
                case 2:
                    Toast.makeText(context, "Head-Shake Detected!", Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    }

    public FaceDirection getDirection() { //returns most recent face direction
        if(faceDirection.size() > 0)
            return faceDirection.get(faceDirection.size() - 1);
        else
            return FaceDirection.NULL;
    }

    /**
     * @return 0 if no actions found, 1 if nod, 2 if shake
     */
    public int detectAction() {
        if (faceDirection.size() < 4) return 0; //no action could happen if not enough history

        if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.UP &&
                faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                faceDirection.get(faceDirection.size() - 2) == FaceDirection.DOWN &&
                faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A nod
            clearHistory();
            return 1; //nod detected.
        } else if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.LEFT &&
                faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                faceDirection.get(faceDirection.size() - 2) == FaceDirection.RIGHT &&
                faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A shake starting to the left
            clearHistory();
            return 2;
        } else if (faceDirection.get(faceDirection.size() - 4) == FaceDirection.RIGHT &&
                faceDirection.get(faceDirection.size() - 3) == FaceDirection.CENTER &&
                faceDirection.get(faceDirection.size() - 2) == FaceDirection.LEFT &&
                faceDirection.get(faceDirection.size() - 1) == FaceDirection.CENTER) { //A shake starting to the right
            clearHistory();
            return 2;
        }
        return 0;
    }

    public void clearHistory() {
        faceDirection.clear();
    }
}