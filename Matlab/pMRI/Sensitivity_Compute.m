function Sense = Sensitivity_Compute(ks_cnenter, Sen_size)
% Function: Sensitivity_Compute(ks_cnenter, Sen_size) compute the sensitivity by the
%           k-space data block, by padding and sos
%
% Parameter: ks_cnenter: k-space data, 3D matrix
%            Sen_size: size of sensitivity, 3D matrix
%            
% Return: Sense: Sensitivity
%
% Sunrise
ks_zero_fill = zpad(ks_cnenter, Sen_size);
Img_zero_fill = ifft2_3D(ks_zero_fill); 
Img_clip_sos = sos(Img_zero_fill) + eps;
Sense = Img_zero_fill ./ repmat(Img_clip_sos, 1, 1, size(Img_zero_fill, 3));
end
