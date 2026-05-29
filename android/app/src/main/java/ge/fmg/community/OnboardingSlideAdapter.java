package ge.fmg.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class OnboardingSlideAdapter extends RecyclerView.Adapter<OnboardingSlideAdapter.SlideHolder> {

  static final class Slide {
    final int sceneRes;
    final int illustrationRes;
    final int titleRes;
    final int bodyRes;

    Slide(int sceneRes, int illustrationRes, int titleRes, int bodyRes) {
      this.sceneRes = sceneRes;
      this.illustrationRes = illustrationRes;
      this.titleRes = titleRes;
      this.bodyRes = bodyRes;
    }
  }

  private final Slide[] slides;

  OnboardingSlideAdapter(Slide[] slides) {
    this.slides = slides;
  }

  @NonNull
  @Override
  public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext())
            .inflate(R.layout.layout_onboarding_slide, parent, false);
    return new SlideHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
    Slide slide = slides[position];
    holder.sceneBg.setImageResource(slide.sceneRes);
    holder.illustration.setImageResource(slide.illustrationRes);
    holder.title.setText(holder.itemView.getContext().getString(slide.titleRes));
    holder.body.setText(holder.itemView.getContext().getString(slide.bodyRes));
  }

  @Override
  public int getItemCount() {
    return slides.length;
  }

  static class SlideHolder extends RecyclerView.ViewHolder {
    final ImageView sceneBg;
    final ImageView illustration;
    final TextView title;
    final TextView body;

    SlideHolder(@NonNull View itemView) {
      super(itemView);
      sceneBg = itemView.findViewById(R.id.onboarding_scene_bg);
      illustration = itemView.findViewById(R.id.onboarding_illustration);
      title = itemView.findViewById(R.id.onboarding_title);
      body = itemView.findViewById(R.id.onboarding_body);
    }
  }
}
