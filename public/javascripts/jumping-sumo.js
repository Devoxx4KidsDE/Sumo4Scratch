$(function () {

    // disable logging
    let logger = createLogger();
    if (!(getURLParameter('logger', false) === 'true')) {
        logger.disableLogger();
    }

    let refreshVideoTimeInMs = getURLParameter('refreshVideoTimeInMs', 50);
    let refreshVideoDisabledTimeInMs = getURLParameter('refreshVideoDisabledRetryTimeInMs', 2000);
    let refreshPictureTimeInMs = getURLParameter('refreshPictureTimeInMs', 2000);
    let pictureOnMonitor = false;

    $('#video').on('click', function () {
        pictureOnMonitor = false;
        highlight($(this));
        refreshVideo();
    });

    $('.small-pictures:not(#video)').on('click', function () {
        pictureOnMonitor = true;
        highlight($(this));
        $('#monitor').attr('src', $(this).attr('src'));
    });

    refreshVideo();
    refreshPhotos();

    function refreshVideo() {
        console.log('//> trying to refresh video');

        let videoOn = isVideoOn();
        let frameAvailable = isFrameAvailable();
        if (videoOn && frameAvailable) {
            console.log('updating monitor with new frame');
            let videoImage = 'app/videoframe?t=' + new Date().getTime();

            if (!pictureOnMonitor) {
                console.log('picture on monitor');
                $('#monitor').removeClass('opacity').attr('src', videoImage);
            }

            $('#video').removeClass('opacity').attr('src', videoImage);
            setTimeout(refreshVideo, refreshVideoTimeInMs);
        }
        else {
            console.log('Cannot display Video stream: video is ' + videoOn + ' - frame is available ' + frameAvailable);
            if (!pictureOnMonitor) {
                $('#monitor').addClass('opacity').attr('src', 'assets/images/novideo.jpg');
            }
            $('#video').addClass('opacity').attr('src', 'assets/images/novideo.jpg');
            setTimeout(refreshVideo, refreshVideoDisabledTimeInMs);
        }
    }

    function refreshPhotos() {
        console.log('//> trying to refresh photos');
        $('.small-pictures:not(#video)').each(function (index) {
            let $this = $(this);
            let picturePath = 'app/photo/' + index + '?t=' + new Date().getTime();
            checkIfImageExists(picturePath, function (exists) {
                if (exists) {
                    $this.removeClass('opacity').attr('src', picturePath);
                } else {
                    $this.addClass('opacity');
                }
            });
        });

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

    function getURLParameter(name, defaultValue = undefined) {
        return decodeURIComponent((new RegExp('[?|&]' + name + '=' + '([^&;]+?)(&|#|;|$)').exec(location.search) || [null, ''])[1].replace(/\+/g, '%20')) || defaultValue;
    }

    function createLogger() {
        let oldConsoleLog = null;
        let pub = {};

        pub.enableLogger = function enableLogger() {
            if (oldConsoleLog === null)
                return;

            window['console']['log'] = oldConsoleLog;
        };

        pub.disableLogger = function disableLogger() {
            oldConsoleLog = console.log;
            window['console']['log'] = function () {
            };
        };

        return pub;
    }

    function highlight($this) {
        $('.small-pictures').removeClass('border-highlight');
        $this.addClass('border-highlight');
    }
});