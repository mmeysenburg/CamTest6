package ist.doane.edu.camtest6;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

/**
 * Second attempt at Android camera2 tutorial from
 * https://willowtreeapps.com/ideas/camera2-and-you-leveraging-android-lollipops-new-camera/
 */
public class MainActivity extends AppCompatActivity {

    private TextureView mPreviewView;
    private SurfaceTexture mPreviewSurfaceTexture;
    private ImageReader mImageReader;
    private CameraDevice mCamera;
    private Surface mPreviewSurface;
    private Surface mJpegCaptureSurface;
    private CameraCaptureSession mSession;
    private TotalCaptureResult mCaptureResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // set up the camera and start the preview
        setupCamera();
    }

    public void onClick(View view) {
        try {
            CaptureRequest.Builder request =
                    mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            request.addTarget(mJpegCaptureSurface);

            mSession.capture(request.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    mCaptureResult = result;
                }
            }, null);
        } catch (CameraAccessException e) {
            Toast.makeText(MainActivity.this,
                    "Can't access camera in onClick()", Toast.LENGTH_LONG).show();
        }
    }

    private void setupCamera() {
        mPreviewView = (TextureView) findViewById(R.id.textureView);
        mPreviewView.setSurfaceTextureListener(new MyTextureListener());
    }

    private class MyTextureListener implements TextureView.SurfaceTextureListener {
        private int width;
        private int height;

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            this.width = width;
            this.height = height;

            mPreviewSurfaceTexture = surfaceTexture;
            mPreviewSurfaceTexture.setDefaultBufferSize(1056, 864);

            // obtain camera data
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                String cameraID = cameraManager.getCameraIdList()[0];

                CameraCharacteristics cameraCharacteristics =
                        cameraManager.getCameraCharacteristics(cameraID);

                StreamConfigurationMap streamConfigurationMap =
                        cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                Size[] jpegSizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);

                mImageReader = ImageReader.newInstance(jpegSizes[0].getWidth(), jpegSizes[0].getHeight(),
                        ImageFormat.JPEG, 1);
                mImageReader.setOnImageAvailableListener(new ReaderHandler(), null);

                mPreviewSurface = new Surface(mPreviewSurfaceTexture);
                mJpegCaptureSurface = mImageReader.getSurface();

                int permissionCheck = ContextCompat.checkSelfPermission(MainActivity.this,
                        Manifest.permission.CAMERA);
                if(permissionCheck == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(cameraID, new CameraCallback(), null);
                } else {
                    Toast.makeText(MainActivity.this, "cam denied", Toast.LENGTH_LONG).show();
                }

            } catch (CameraAccessException e) {
                Toast.makeText(MainActivity.this, "cam info issue", Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {

            // if the drawing surface is going away, release the camera
            if(mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // if the size of the drawing surface is changing, release the camera
            if(mCamera != null) {
                mCamera.close();
                mCamera = null;
            }

        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    }

    private class ReaderHandler implements ImageReader.OnImageAvailableListener {
        public void onImageAvailable(ImageReader reader) {
            // save -- or something? -- the JPEG
            Image img = reader.acquireLatestImage();

            int id = img.getFormat();
            String m = "";
            if(id == ImageFormat.JPEG) {
                m = "JPEG ";
            } else {
                m = Integer.toString(id) + " ";
            }
            m += img.getWidth() + " x " + img.getHeight();
            Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();

            img.close();
        }

    }

    private class CameraCallback extends CameraDevice.StateCallback {
        public void onDisconnected(CameraDevice camera) {
            if(camera != null) {
                camera.close();
            }
        }

        public void onOpened(CameraDevice camera) {
            mCamera = camera;

            List<Surface> surfaces = Arrays.asList(mPreviewSurface, mJpegCaptureSurface);
            try {
                mCamera.createCaptureSession(surfaces, new CamCap(), null);
            } catch(CameraAccessException e) {
                Toast.makeText(MainActivity.this, "Can't create cap session", Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }

        public void onError(CameraDevice camera, int error) {
            String m = "";
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    m = "error camera device";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    m = "error camera disabled";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    m = "error camera in use";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    m = "error camera service";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    m = "error max cameras in use";
                    break;
            }
            Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show();
        }
    }

    private class CamCap extends CameraCaptureSession.StateCallback {
        public void onConfigureFailed(CameraCaptureSession session) {
            Toast.makeText(MainActivity.this, "CamCap config", Toast.LENGTH_LONG).show();
        }

        public void onConfigured(CameraCaptureSession session) {
            mSession = session;

            try {
                CaptureRequest.Builder request = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(mPreviewSurface);

                mSession.setRepeatingRequest(request.build(), new CameraCaptureSession.CaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }
}
