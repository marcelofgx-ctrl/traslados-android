package uy.com.mapatrayectos;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/**
 * R22.6: single illustrated reminder callout for the in-app map and optional Android overlay.
 * The right-pointing triangle is a child of the SAME view as the message card, and its
 * endpoint is anchored to the actual quick-action star; it is never an independent popup.
 */
public final class ReminderCallout extends FrameLayout {
    public static final int CARD_WIDTH_DP=190,TAIL_WIDTH_DP=13;
    public static final int TOTAL_WIDTH_DP=CARD_WIDTH_DP+TAIL_WIDTH_DP;
    public static final int TAIL_TOP_DP=12,TAIL_HEIGHT_DP=20;
    public static final int TAIL_CENTER_DP=TAIL_TOP_DP+TAIL_HEIGHT_DP/2;
    private final int petrol=Color.rgb(5,49,56),gold=Color.rgb(212,174,96),ivory=Color.rgb(252,250,241);
    private Runnable doneAction,postponeAction,dismissAction;
    private LinearLayout cardView;
    private View pointerView;
    private boolean tailOnLeft=false;
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));
        if(stroke!=0)d.setStroke(dp(1),stroke);return d;
    }
    private TextView title(String s,int sp,int color,boolean bold){
        TextView t=new TextView(getContext());t.setText(s);t.setTextColor(color);t.setTextSize(sp);
        if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        return t;
    }
    public ReminderCallout(Context c,String message,Runnable done,Runnable postpone,Runnable close){
        super(c);doneAction=done;postponeAction=postpone;dismissAction=close;
        setClipChildren(false);setClipToPadding(false);
        setElevation(dp(12));
        LinearLayout card=new LinearLayout(c);cardView=card;card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(9),dp(7),dp(9),dp(8));
        card.setBackground(bg(ivory,gold,14));
        LayoutParams cp=new LayoutParams(dp(CARD_WIDTH_DP),LayoutParams.WRAP_CONTENT,Gravity.LEFT|Gravity.TOP);
        addView(card,cp);

        LinearLayout head=new LinearLayout(c);head.setOrientation(LinearLayout.HORIZONTAL);head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar=new ImageView(c);avatar.setImageResource(R.drawable.app_icon);
        avatar.setBackground(bg(petrol,gold,22));avatar.setPadding(dp(3),dp(3),dp(3),dp(3));
        head.addView(avatar,new LinearLayout.LayoutParams(dp(25),dp(25)));
        LinearLayout headings=new LinearLayout(c);headings.setOrientation(LinearLayout.VERTICAL);
        TextView h=title("🔔  TE RECUERDO ALGO",10,petrol,true);h.setSingleLine(true);
        headings.addView(h);
        TextView sub=title("Mapa Trayectos · Aviso personal",8,Color.rgb(85,100,99),false);sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);headings.addView(sub);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,LayoutParams.WRAP_CONTENT,1);
        hp.leftMargin=dp(5);head.addView(headings,hp);
        card.addView(head);
        TextView msg=title(message==null?"Recordatorio":message,14,petrol,true);
        msg.setMaxLines(3);msg.setEllipsize(android.text.TextUtils.TruncateAt.END);
        msg.setPadding(dp(2),dp(8),dp(2),dp(8));card.addView(msg);
        LinearLayout actions=new LinearLayout(c);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        TextView later=title("+10 MIN",11,petrol,true);later.setGravity(Gravity.CENTER);
        later.setBackground(bg(Color.rgb(237,222,191),0,10));
        TextView finish=title("HECHO",11,ivory,true);finish.setGravity(Gravity.CENTER);
        finish.setBackground(bg(petrol,0,10));
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,dp(33),1);ap.rightMargin=dp(7);
        actions.addView(later,ap);actions.addView(finish,new LinearLayout.LayoutParams(0,dp(33),1));
        card.addView(actions);
        later.setOnClickListener(v->{if(postponeAction!=null)postponeAction.run();});
        finish.setOnClickListener(v->{if(doneAction!=null)doneAction.run();});
        card.setOnClickListener(v->{if(dismissAction!=null)dismissAction.run();});

        // The point is at x=TOTAL_WIDTH_DP, not a stand-alone rectangle.
        View pointer=new View(c){
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas){
                float w=getWidth(),h=getHeight();
                Path outline=new Path();
                if(tailOnLeft){
                    outline.moveTo(w,0);outline.lineTo(.5f,h/2f);outline.lineTo(w,h);
                }else{
                    outline.moveTo(0,0);outline.lineTo(w-.5f,h/2f);outline.lineTo(0,h);
                }
                outline.close();
                paint.setStyle(Paint.Style.FILL);paint.setColor(gold);canvas.drawPath(outline,paint);
                Path center=new Path();
                if(tailOnLeft){
                    center.moveTo(w,dp(2));center.lineTo(dp(2),h/2f);
                    center.lineTo(w,h-dp(2));
                }else{
                    center.moveTo(0,dp(2));center.lineTo(w-dp(2),h/2f);
                    center.lineTo(0,h-dp(2));
                }center.close();
                paint.setColor(ivory);canvas.drawPath(center,paint);
            }
        };
        LayoutParams pp=new LayoutParams(dp(TAIL_WIDTH_DP+1),dp(TAIL_HEIGHT_DP),Gravity.LEFT|Gravity.TOP);
        pp.leftMargin=dp(CARD_WIDTH_DP-1);pp.topMargin=dp(TAIL_TOP_DP);
        addView(pointer,pp);pointerView=pointer;
        setContentDescription("Recordatorio de Mapa Trayectos: "+message+". Posponer diez minutos o marcar hecho.");
    }
    /**
     * R23.0: slide the tail to the actual anchor center, and flip its direction
     * when the floating shortcut sits on the left edge. The tail remains part
     * of this SAME callout; callers refuse any placement that would detach it.
     */
    public void setAnchorPlacement(boolean onLeft,int centerYPx){
        tailOnLeft=onLeft;
        FrameLayout.LayoutParams cardLp=(FrameLayout.LayoutParams)cardView.getLayoutParams();
        int cardLeft=onLeft?dp(TAIL_WIDTH_DP):0;
        if(cardLp.leftMargin!=cardLeft){cardLp.leftMargin=cardLeft;cardView.setLayoutParams(cardLp);}
        FrameLayout.LayoutParams arrowLp=(FrameLayout.LayoutParams)pointerView.getLayoutParams();
        int tipLeft=onLeft?0:dp(CARD_WIDTH_DP-1);
        int arrowTop=centerYPx-dp(TAIL_HEIGHT_DP)/2;
        if(arrowLp.leftMargin!=tipLeft||arrowLp.topMargin!=arrowTop){
            arrowLp.leftMargin=tipLeft;arrowLp.topMargin=arrowTop;
            pointerView.setLayoutParams(arrowLp);
        }
        pointerView.invalidate();
    }
    public void reveal(){
        setAlpha(0f);setTranslationX(dp(10));
        animate().alpha(1f).translationX(0).setDuration(330)
            .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    public void vanish(Runnable after){
        animate().alpha(0f).translationX(dp(9)).setDuration(190).withEndAction(after).start();
    }
}
