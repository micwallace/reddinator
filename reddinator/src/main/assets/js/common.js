
function LightenDarkenColor(col,amt) {
    col = parseInt(col,16);
    return (((col & 0x0000FF) + amt) | ((((col>> 8) & 0x00FF) + amt) << 8) | (((col >> 16) + amt) << 16)).toString(16);
}

function setTheme(themeColors){
    $("body").css("background-color", themeColors["background_color"]);
    $("#loading_view, .reply_expand, .more_box").css("color", themeColors["load_text"]);
    $(".border, .sub-border").css('border-color', themeColors['comments_border']);
    $(".comment_text").css("color", themeColors["comment_text"]?themeColors["comment_text"]:themeColors["headline_text"]);
    $(".comment_user").css("color", themeColors["source_text"]);
    $(".message_type, .message_subject").css("color", themeColors["load_text"]);
    $(".fa-star").css("color", themeColors["votes_icon"]);
    $(".comment_score").css("color", themeColors["votes_text"]);
    $(".fa-comment").css("color", themeColors["comments_icon"]);
    $(".comment_reply_count").css("color", themeColors["comments_text"]);
    $("button").css("background-color", themeColors["header_color"]);
    $("body").show();
}

function addCssFile(url){
    var link = document.createElement("link");
    link.href = url;
    link.type = "text/css";
    link.rel = "stylesheet";
    link.media = "screen,print";
    document.getElementsByTagName("head")[0].appendChild(link);
}

function htmlDecode(input){
    var e = document.createElement('div');
    e.innerHTML = input;
    return e.childNodes.length === 0 ? "" : e.childNodes[0].nodeValue;
}