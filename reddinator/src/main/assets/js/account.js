// set article from document hash
var username;
var section;
var isMessages = false;
var color_vote = "#A5A5A5";
var color_upvote_active = "#FF8B60";
var color_downvote_active = "#9494FF";

function init(themeColors, user, sect){
    username = user;
    section = sect;
    if (sect=="unread" || sect=="inbox" || sect=="sent")
        isMessages = true;

    viewType = "account";
    setTheme(themeColors);
}

function populateFeed(json, append){
    var data = JSON.parse(json);
    $("#loading_view").hide();
    $("#base").show();
    if (append)
        $("#more").remove();
    var lastItemId = 0;
    for (var i in data){
        lastItemId = data[i].data.name;
        if (data[i].kind=="t1"){
            appendComment(data[i].data, false);
        } else if (data[i].kind=="t3") {
            appendPost(data[i].data, false);
        } else if (data[i].kind=="t4") {
            appendMessage(data[i].data, false);
        }
    }
    appendMoreButton(lastItemId);
}

function showLoadingView(text){
    var loading = $("#loading_view");
    loading.children("h4").text(text);
    $("#base").hide();
    loading.show();
}
// java bind functions
function reloadFeed(){
    showLoadingView("Loading...");
    $("#base").html('');
    Reddinator.reloadFeed($("#sort_select").val());
}

function loadFeedStart(){
    showLoadingView("Loading...");
    $("#base").html('');
}

function loadMore(moreId){
    Reddinator.loadMore(moreId);
}

function message(elem){
    var text = elem.siblings('.message_textarea').val();
    var id = elem.parent().parent().attr('id');
    if (text==""){
        alert("Enter some text for the message.");
        messageCallback(id, false);
        return;
    }
    var to = elem.parent().parent().data('to');
    var subject = elem.parent().parent().data('subject');
    if (subject.indexOf("re:")!==0)
        subject = "re: "+subject;
    Reddinator.message(to, subject, text, id);
}

function messageCallback(parentId, success){
    //console.log("message callback called");
    var postElem = $("#"+parentId+" > .post_box");
    if (success){
        postElem.children('textarea').val('');
        postElem.hide();
    }
    postElem.children("button").prop("disabled", false);
}

function commentCallback(parentId, commentData){
    //console.log("comment callback called");
    var postElem = $("#"+parentId+" > .post_box");
    if (commentData){
        commentData = JSON.parse(commentData);
        if (parentId.indexOf("t3_")!==-1){
            $("#post_comment_button").show();
            // in case of submitting first comment
            $("#loading_view").hide();
            $("#base").show();
        }
        postElem.children('textarea').val('');
        postElem.hide();
        appendComment(commentData, true, parentId);
        $("#loading_view").hide();
    }
    postElem.children("button").prop("disabled", false);
}

function unSave(thingId){
    var answer = confirm("Are you sure you want to unsave?");
    if (answer){
        Reddinator.unSave(thingId);
    }
}

function unHide(thingId){
    var answer = confirm("Are you sure you want to unhide?");
    if (answer){
        Reddinator.unHide(thingId);
    }
}

function noMoreCallback(moreId){
    $("#more h5").text("There's nothing more here");
}

function resetMoreClickEvent(moreId){
    var moreElem = $("#more");
    moreElem.children("h5").text('Load '+moreElem.data('rlength')+' More');
    moreElem.one('click',
        {lastItemId: moreElem.data('rname')},
        function(event){
            $(this).children("h5").text("Loading...");
            loadMore(event.data.lastItemId);
        }
    );
}

function appendMoreButton(lastItemId){
    var moreElem = $("#more_template").clone().show();
    moreElem.attr("id", "more");
    moreElem.children("h5").text("Load more");
    moreElem.data('rname', lastItemId);
    moreElem.one('click',
        {lastItemId: lastItemId},
        function(event){
            $(this).children("h5").text("Loading...");
            loadMore(event.data.lastItemId);
        }
    );
    moreElem.css("margin-right", "0").appendTo("#base");
}

function appendPost(postData, prepend){
    var postElem = $("#post_template").clone().show();
        postElem.attr("id", postData.name);
        postElem.data('url', postData.url);
        postElem.data('permalink', postData.permalink);
        postElem.data('likes', postData.likes);
        postElem.find(".post_text").html(postData.title);
        postElem.find(".post_domain").text(postData.subreddit+" - "+postData.domain);
        postElem.find(".post_score").text(postData.hide_score?'hidden':postData.score);
        postElem.find(".comment_count").text(postData.num_comments);
        // check if likes
        if (postData.hasOwnProperty('likes')){
            if (postData.likes==true){
                postElem.find(".upvote").css("color", color_upvote_active);
            } else if (postData.likes==false) {
                postElem.find(".downvote").css("color", color_downvote_active);
            }
            postElem.data("likes", postData.likes);
        } else {
            postElem.data("likes", null);
        }
        // check thumbnail
        var thumbnail = postData.thumbnail;
        if (thumbnail && thumbnail!=""){
            if (thumbnail=="nsfw" || thumbnail=="self" || thumbnail=="default") {
                switch (thumbnail) {
                    case "nsfw":
                        thumbnail = "file:///android_asset/images/nsfw.png";
                        break;
                    case "default":
                    case "self":
                    default:
                        thumbnail = "file:///android_asset/images/self_default.png";
                        break;
                }
            }
            postElem.find(".post_thumb").attr("src", thumbnail).show();
            postElem.find(".post_text").css('margin-left', '76px');
            postElem.find(".post_main").css('min-height', '75px')
        }
        // check if author
        /*if (postData.author==username)
            postElem.find(".user_option").show();*/
        var flag = postElem.find(".distinguish_flag");
        if (postData.author==username){
            flag.text("[S]");
            flag.show();
        }
        if (postData.distinguished!=null){
            switch(postData.distinguished){
                case "moderator":
                    flag.text("[M]");
                    flag.css("color", "#30925E");
                    break;
                case "admin":
                    flag.text("[A]");
                    flag.css("color", "#F82330");
                    break;
                case "special":
                    flag.text("[Δ]");
                    flag.css("color", "#C22344");
                    break;
            }
            flag.show();
        }

        var opt = postElem.find(".remove_option");
        switch (section){
            case "saved":
                opt.show();
                opt.attr("onclick", "unSave('"+postData.name+"');");
                break;
            case "hidden":
                opt.show();
                opt.attr("onclick", "unHide('"+postData.name+"');");
                break;
        }

        if (prepend){
            postElem.prependTo("#base");
        } else {
            postElem.appendTo("#base");
        }
}

function appendComment(commentData, prepend, parentId){
    //console.log(JSON.stringify(commentData));
    var commentElem = $("#comment_template").clone().show();
    commentElem.attr("id", commentData.name);
    commentElem.data("comment_md", commentData.body);
    var text = htmlDecode(commentData.body_html.replace(/\n\n/g, "\n").replace("\n&lt;/div&gt;", "&lt;/div&gt;")); // clean up extra line breaks
    commentElem.find(".comment_text").html(text);
    commentElem.find(".comment_user").text('/u/'+commentData.author).attr('href', 'https://www.reddit.com/u/'+commentData.author);
    if (isMessages){
        commentElem.find(".comment_scores").hide();
        commentElem.find(".message_type").text("("+commentData.subject+")").show();
        commentElem.find(".message_subject").text(commentData.link_title).show();
        commentElem.data("context", commentData.context);
    } else {
        commentElem.find(".comment_score").text(commentData.score_hidden?'hidden':commentData.score);
        commentElem.find(".comment_reply_count").hide();
        // build context url
        commentElem.data("context", "/r/"+commentData.subreddit+"/comments/"+commentData.link_id.split("_")[1]+"//"+commentData.id+"/?context=3");
    }
    // check if likes
    if (commentData.hasOwnProperty('likes')){
        if (commentData.likes==true){
            commentElem.find(".upvote").css("color", color_upvote_active);
        } else if (commentData.likes==false) {
            commentElem.find(".downvote").css("color", color_downvote_active);
        }
        commentElem.data("likes", commentData.likes);
    } else {
        commentElem.data("likes", null);
    }
    // check if author
    if (commentData.author==username)
        commentElem.find(".user_option").show();
    var flag = commentElem.find(".distinguish_flag");
    if (commentData.link_author==username){
        flag.text("[S]");
        flag.show();
    }
    if (commentData.distinguished!=null){
        switch(commentData.distinguished){
            case "moderator":
                flag.text("[M]");
                flag.css("color", "#30925E");
                break;
            case "admin":
                flag.text("[A]");
                flag.css("color", "#F82330");
                break;
            case "special":
                flag.text("[Δ]");
                flag.css("color", "#C22344");
                break;
        }
        flag.show();
    }
    if (section=="saved"){
        var opt = commentElem.find(".remove_option");
        opt.show();
        opt.attr("onclick", "unSave('"+commentData.name+"');");
    }
    if (parentId==null){
        parentId = "#base";
    } else {
        parentId = "#"+parentId+" .comment_replies";
        $(parentId).show();
    }
    if (prepend){
        commentElem.prependTo(parentId);
    } else {
        commentElem.appendTo(parentId);
    }
}

function appendMessage(messageData, prepend){
    //console.log(JSON.stringify(messageData));
    var messageElem = $("#message_template").clone().show();
    messageElem.attr("id", messageData.name);
    messageElem.data("to", (section=="sent"?messageData.dest:messageData.author));
    messageElem.data("subject", messageData.subject);
    var text = htmlDecode(messageData.body_html.replace(/\n\n/g, "\n").replace("\n&lt;/div&gt;", "&lt;/div&gt;")); // clean up extra line breaks
    messageElem.find(".message_text").html(text);
    var authorText = (section=="sent"?messageData.dest:messageData.author);
    messageElem.find(".message_user").text('/u/'+authorText).attr('href', 'https://www.reddit.com/u/'+authorText);
    messageElem.find(".message_subject").text(messageData.subject);
    // check if likes
    /*if (messageData.hasOwnProperty('likes')){
        if (messageData.likes==1){
            messageElem.find(".upvote").css("color", color_upvote_active);
        } else if (messageData.likes==-1) {
            messageElem.find(".downvote").css("color", color_downvote_active);
        }
    }*/
    // check if author
    /*if (messageData.author==username)
        messageElem.find(".user_option").show();*/

    /*if (messageData.link_author==username){
        flag.text("[S]");
        flag.css("visibility", "visible");
    }*/
    var flag = messageElem.find(".distinguish_flag");
    if (messageData.distinguished!=null){
        switch(messageData.distinguished){
            case "moderator":
                flag.text("[M]");
                flag.css("color", "#30925E");
                break;
            case "admin":
                flag.text("[A]");
                flag.css("color", "#F82330");
                break;
            case "special":
                flag.text("[Δ]");
                flag.css("color", "#C22344");
                break;
        }
        flag.show();
    }
    if (prepend){
        messageElem.prependTo("#base");
    } else {
        messageElem.appendTo("#base");
    }
}

$(function(){

    $(document).on('click', ".upvote", function(e){
        vote($(this).parent().parent().attr("id"), 1);
        e.stopPropagation();
    });
    $(document).on('click', ".downvote", function(e){
        vote($(this).parent().parent().attr("id"), -1);
        e.stopPropagation();
    });
    var cMdeEditor = null;
    $(document).on('click', ".post_toggle", function(){
        var elem = $(this).parent().parent().parent().children(".post_reply");
        if (cMdeEditor!=null){
            cMdeEditor.toTextArea();
            cMdeEditor = null;
        }
        if (elem.is(":visible")){
            elem.hide();
        } else {
            $('.message_reply, .post_reply').hide();
            elem.show();
            if (useMdEditor){
                cMdeEditor = initialiseMarkdownEditor(elem.children("textarea"));
            } else {
                elem.children("textarea").focus();
            }
        }
    });
    var mMdEditor = null;
    $(document).on('click', ".message_reply_toggle", function(){
        var elem = $(this).parent().parent().parent().children(".message_reply");
        if (mMdEditor!=null){
            mMdEditor.toTextArea();
            mMdEditor = null;
        }
        if (elem.is(":visible")){
            elem.hide();
        } else {
            $('.message_reply, .post_reply').hide();
            elem.show();
            if (useMdEditor){
                mMdEditor = initialiseMarkdownEditor(elem.children("textarea"));
            } else {
                elem.children("textarea").focus();
            }
        }
    });
    $(document).on('click', ".post_main", function(e){
        var elem = $(this).parent();
        Reddinator.openRedditPost(elem.attr("id"), elem.data('url'), elem.data('permalink'), elem.data('likes'));
        e.stopImPropagation();
    });
});