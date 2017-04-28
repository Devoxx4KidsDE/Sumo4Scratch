$(function () {

    let refreshVideoTimeInMs = 50;
    let refreshOnErrorTimeInMs = 2000;
    let refreshPictureTimeInMs = 2000;
    let pictureOnMonitor = false;

    $('#monitor').on('click', function () {
        pictureOnMonitor = false;
        refreshVideo();
    });

    $('.small-pictures').on('click', function () {
        pictureOnMonitor = true;
        $('#monitor').attr('src', $(this).attr('src'));
    });

    refreshVideo();
    refreshPhotos();

    function refreshVideo() {
        console.log('//> trying to refresh video');

        if (pictureOnMonitor) {
            console.log('picture on monitor');
            setTimeout(refreshVideo, refreshOnErrorTimeInMs);
            return;
        }

        let videoOn = isVideoOn();
        let frameAvailable = isFrameAvailable();
        if (videoOn && frameAvailable) {
            console.log('updating monitor with new frame');
            $('#monitor').removeClass('opacity').attr('src', 'app/videoframe?t=' + new Date().getTime());
            setTimeout(refreshVideo, refreshVideoTimeInMs);
        }
        else {
            console.log('Cannot display Video stream: video is' + videoOn + ' - frame is available ' + frameAvailable);
            $('#monitor').addClass('opacity').attr('src', 'assets/images/novideo.jpg');
            setTimeout(refreshVideo, refreshOnErrorTimeInMs);
        }
    }

    function refreshPhotos() {
        console.log('//> trying to refresh photos');
        for (let i = 0; i < 9; i++) {
            let picturePath = 'app/photo/' + i + '?t=' + new Date().getTime();
            checkIfImageExists(picturePath, function (exists) {
                if (exists) {
                    $('#p' + i).removeClass('opacity').attr('src', picturePath);
                } else {
                    $('#p' + i).addClass('opacity');
                }
            });
        }

        setTimeout(refreshPhotos, refreshPictureTimeInMs);
    }

    function checkIfImageExists(url, callback) {
        let img = new Image();
        img.onload = function () {
            callback(true);
        };
        img.onerror = function () {
            callback(false);
        };
        img.src = url;
    }

    function isVideoOn() {
        let result;
        $.ajax({
            type: 'GET',
            url: "app/isvideoon",
            async: false,
            success: function (text) {
                console.log('video is on?', text);
                result = (text === 'yes');
            },
            error: function (text) {
                console.log('video error:', text);

                result = false;
            }
        });

        return result;
    }


    function isFrameAvailable() {
        let result;

        $.ajax({
            type: 'GET',
            url: "app/isframeavailable",
            async: false,
            success: function (text) {
                console.log('frame available?', text);
                result = (text === 'yes');
            },
            error: function (text) {
                console.log('frame error:', text);
                result = false;
            }
        });

        return result;
    }
});